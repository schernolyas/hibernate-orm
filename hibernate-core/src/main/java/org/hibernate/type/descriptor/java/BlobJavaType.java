/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.type.descriptor.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.SQLException;

import org.hibernate.HibernateException;
import org.hibernate.SharedSessionContract;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.BinaryStream;
import org.hibernate.engine.jdbc.BlobImplementer;
import org.hibernate.engine.jdbc.BlobProxy;
import org.hibernate.engine.jdbc.WrappedBlob;
import org.hibernate.engine.jdbc.internal.BinaryStreamImpl;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * Descriptor for {@link Blob} handling.
 * <p>
 * Note, {@link Blob}s really are mutable (their internal state can in fact be mutated).  We simply
 * treat them as immutable because we cannot properly check them for changes nor deep copy them.
 *
 * @author Steve Ebersole
 * @author Brett Meyer
 * @author Loïc Lefèvre
 */
public class BlobJavaType extends AbstractClassJavaType<Blob> {
	public static final BlobJavaType INSTANCE = new BlobJavaType();

	public static class BlobMutabilityPlan implements MutabilityPlan<Blob> {
		public static final BlobMutabilityPlan INSTANCE = new BlobMutabilityPlan();

		@Override
		public boolean isMutable() {
			return false;
		}

		@Override
		public Blob deepCopy(Blob value) {
			return value;
		}

		@Override
		public Serializable disassemble(Blob value, SharedSessionContract session) {
			throw new UnsupportedOperationException( "Blobs are not cacheable" );
		}

		@Override
		public Blob assemble(Serializable cached, SharedSessionContract session) {
			throw new UnsupportedOperationException( "Blobs are not cacheable" );
		}
	}

	public BlobJavaType() {
		super( Blob.class, BlobMutabilityPlan.INSTANCE, IncomparableComparator.INSTANCE );
	}

	@Override
	public String extractLoggableRepresentation(Blob value) {
		return value == null ? "null" : "{blob}";
	}

	@Override
	public String toString(Blob value) {
		final byte[] bytes;
		try {
			bytes = DataHelper.extractBytes( value.getBinaryStream() );
		}
		catch ( SQLException e ) {
			throw new HibernateException( "Unable to access blob stream", e );
		}
		return PrimitiveByteArrayJavaType.INSTANCE.toString( bytes );
	}

	@Override
	public Blob fromString(CharSequence string) {
		return BlobProxy.generateProxy( PrimitiveByteArrayJavaType.INSTANCE.fromString( string ) );
	}

	@Override
	public int extractHashCode(Blob value) {
		return System.identityHashCode( value );
	}

	@Override
	public boolean areEqual(Blob one, Blob another) {
		return one == another;
	}

	@Override
	public Blob getReplacement(Blob original, Blob target, SharedSessionContractImplementor session) {
		return session.getJdbcServices().getJdbcEnvironment().getDialect().getLobMergeStrategy()
				.mergeBlob( original, target, session );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <X> X unwrap(Blob value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		try {
			if ( BinaryStream.class.isAssignableFrom( type ) ) {
				if (value instanceof BlobImplementer blobImplementer) {
					// if the incoming Blob is a wrapper, just pass along its BinaryStream
					return (X) blobImplementer.getUnderlyingStream();
				}
				else {
					// otherwise we need to build a BinaryStream...
					return (X) new BinaryStreamImpl( DataHelper.extractBytes( value.getBinaryStream() ) );
				}
			}
			else if ( byte[].class.isAssignableFrom( type )) {
				if (value instanceof BlobImplementer blobImplementer) {
					// if the incoming Blob is a wrapper, just grab the bytes from its BinaryStream
					return (X) blobImplementer.getUnderlyingStream().getBytes();
				}
				else {
					// otherwise extract the bytes from the stream manually
					return (X) DataHelper.extractBytes( value.getBinaryStream() );
				}
			}
			else if ( Blob.class.isAssignableFrom( type ) ) {
				return (X) getOrCreateBlob( value, options );
			}
		}
		catch ( SQLException e ) {
			throw new HibernateException( "Unable to access blob stream", e );
		}

		throw unknownUnwrap( type );
	}

	private Blob getOrCreateBlob(Blob value, WrapperOptions options) throws SQLException {
		if ( value instanceof WrappedBlob wrappedBlob ) {
			value = wrappedBlob.getWrappedBlob();
		}
		if ( options.getDialect().useConnectionToCreateLob() ) {
			if ( value.length() == 0 ) {
				// empty Blob
				return options.getLobCreator().createBlob( new byte[0] );
			}
			else {
				return options.getLobCreator().createBlob( value.getBytes( 1, (int) value.length() ) );
			}
		}
		else {
			return value;
		}
	}

	@Override
	public <X> Blob wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}
		else if ( value instanceof Blob blob ) {
			return options.getLobCreator().wrap( blob );
		}
		else if ( value instanceof byte[] bytes ) {
			return options.getLobCreator().createBlob( bytes );
		}
		else if ( value instanceof InputStream inputStream ) {
			try {
				return options.getLobCreator().createBlob( inputStream, inputStream.available() );
			}
			catch ( IOException e ) {
				throw unknownWrap( value.getClass() );
			}
		}

		throw unknownWrap( value.getClass() );
	}

	@Override
	public long getDefaultSqlLength(Dialect dialect, JdbcType jdbcType) {
		return dialect.getDefaultLobLength();
	}
}
