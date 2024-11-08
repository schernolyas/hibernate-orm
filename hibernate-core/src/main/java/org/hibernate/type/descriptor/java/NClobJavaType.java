/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.type.descriptor.java;

import java.io.Reader;
import java.io.Serializable;
import java.sql.NClob;
import java.sql.SQLException;

import org.hibernate.HibernateException;
import org.hibernate.SharedSessionContract;
import org.hibernate.engine.jdbc.CharacterStream;
import org.hibernate.engine.jdbc.NClobImplementer;
import org.hibernate.engine.jdbc.NClobProxy;
import org.hibernate.engine.jdbc.WrappedNClob;
import org.hibernate.engine.jdbc.internal.CharacterStreamImpl;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.WrapperOptions;

/**
 * Descriptor for {@link NClob} handling.
 * <p>
 * Note, {@link NClob}s really are mutable (their internal state can in fact be mutated).  We simply
 * treat them as immutable because we cannot properly check them for changes nor deep copy them.
 *
 * @author Steve Ebersole
 * @author Loïc Lefèvre
 */
public class NClobJavaType extends AbstractClassJavaType<NClob> {
	public static final NClobJavaType INSTANCE = new NClobJavaType();

	public static class NClobMutabilityPlan implements MutabilityPlan<NClob> {
		public static final NClobMutabilityPlan INSTANCE = new NClobMutabilityPlan();

		public boolean isMutable() {
			return false;
		}

		public NClob deepCopy(NClob value) {
			return value;
		}

		public Serializable disassemble(NClob value, SharedSessionContract session) {
			throw new UnsupportedOperationException( "Clobs are not cacheable" );
		}

		public NClob assemble(Serializable cached, SharedSessionContract session) {
			throw new UnsupportedOperationException( "Clobs are not cacheable" );
		}
	}

	public NClobJavaType() {
		super( NClob.class, NClobMutabilityPlan.INSTANCE, IncomparableComparator.INSTANCE );
	}

	@Override
	public String extractLoggableRepresentation(NClob value) {
		return value == null ? "null" : "{nclob}";
	}

	public String toString(NClob value) {
		return DataHelper.extractString( value );
	}

	public NClob fromString(CharSequence string) {
		return NClobProxy.generateProxy( string.toString() );
	}

	@Override
	public int extractHashCode(NClob value) {
		return System.identityHashCode( value );
	}

	@Override
	public boolean areEqual(NClob one, NClob another) {
		return one == another;
	}

	@Override
	public NClob getReplacement(NClob original, NClob target, SharedSessionContractImplementor session) {
		return session.getJdbcServices().getJdbcEnvironment().getDialect().getLobMergeStrategy()
				.mergeNClob( original, target, session );
	}

	@SuppressWarnings("unchecked")
	public <X> X unwrap(final NClob value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		try {
			if ( CharacterStream.class.isAssignableFrom( type ) ) {
				if (value instanceof NClobImplementer clobImplementer) {
					// if the incoming NClob is a wrapper, just pass along its BinaryStream
					return (X) clobImplementer.getUnderlyingStream();
				}
				else {
					// otherwise we need to build a BinaryStream...
					return (X) new CharacterStreamImpl( DataHelper.extractString( value.getCharacterStream() ) );
				}
			}
			else if ( NClob.class.isAssignableFrom( type ) ) {
				return (X) getOrCreateNClob( value, options );
			}
		}
		catch ( SQLException e ) {
			throw new HibernateException( "Unable to access nclob stream", e );
		}

		throw unknownUnwrap( type );
	}

	private NClob getOrCreateNClob(NClob value, WrapperOptions options) throws SQLException {
		if ( value instanceof WrappedNClob wrappedNClob ) {
			value = wrappedNClob.getWrappedNClob();
		}
		if ( options.getDialect().useConnectionToCreateLob() ) {
			if ( value.length() == 0 ) {
				// empty NClob
				return options.getLobCreator().createNClob( "" );
			}
			else {
				return options.getLobCreator().createNClob( value.getSubString( 1, (int) value.length() ) );
			}
		}
		else {
			return value;
		}
	}

	public <X> NClob wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		// Support multiple return types from
		// org.hibernate.type.descriptor.sql.ClobTypeDescriptor
		if ( value instanceof NClob clob ) {
			return options.getLobCreator().wrap( clob );
		}
		else if ( value instanceof Reader reader ) {
			return options.getLobCreator().createNClob( DataHelper.extractString( reader ) );
		}

		throw unknownWrap( value.getClass() );
	}
}
