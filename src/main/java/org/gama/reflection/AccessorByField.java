package org.gama.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * @author Guillaume Mary
 */
public class AccessorByField<C, T> extends AbstractAccessor<C, T> implements AccessorByMember<C, T, Field>, IReversibleAccessor<C, T> {
	
	private final Field field;
	
	public AccessorByField(Field field) {
		this.field = field;
		this.field.setAccessible(true);
	}
	
	@Override
	public Field getGetter() {
		return field;
	}
	
	@Override
	protected T doGet(C c) throws IllegalAccessException, InvocationTargetException {
		return (T) getGetter().get(c);
	}
	
	@Override
	public String getGetterDescription() {
		return "accessor for field " + getGetter().toString();
	}
	
	@Override
	public MutatorByField<C, T> toMutator() {
		return new MutatorByField<>(getGetter());
	}
	
	@Override
	public boolean equals(Object other) {
		// we base our implementation on the getter String because a setAccessible() call on the member changes its internal state
		// and I don't think it sould be taken into account for comparison
		return this == other
				|| (other instanceof AccessorByField && getGetter().toString().equals(((AccessorByField) other).getGetter().toString()));
	}
	
	@Override
	public int hashCode() {
		return getGetter().hashCode();
	}
}