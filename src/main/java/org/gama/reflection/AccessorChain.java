package org.gama.reflection;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.lang.Reflections;
import org.gama.lang.bean.Objects;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Collections;
import org.gama.lang.collection.Iterables;

import static org.gama.reflection.Accessors.giveInputType;

/**
 * Chain of {@link IAccessor}s that behaves as a {@link IAccessor}
 * Behavior of null-encountered-values during {@link #get(Object)} is controlled through a {@link NullValueHandler}, by default {@link NullPointerException}
 * will be thrown, see {@link #setNullValueHandler(NullValueHandler)} to change it. This class proposes some other default behavior such as
 * {@link #RETURN_NULL} or {@link #INITIALIZE_VALUE}
 * 
 * @author Guillaume Mary
 */
public class AccessorChain<C, T> extends AbstractAccessor<C, T> implements IReversibleAccessor<C, T> {
	
	public static <IN, A, OUT> AccessorChain<IN, OUT> chain(SerializableFunction<IN, A> function1, SerializableFunction<A, OUT> function2) {
		return new AccessorChain<>(new AccessorByMethodReference<>(function1), new AccessorByMethodReference<>(function2));
	}
	
	/**
	 * Creates a chain that :
	 * - returns null when any accessor on path returns null
	 * - initializes values (instanciate bean) on path when its mutator is used
	 * (voluntary dissimetric behavior)
	 *
	 * @param accessors list of {@link IAccessor} to be used by chain
	 * @see #RETURN_NULL
	 * @see ValueInitializerOnNullValue#giveValueType(IAccessor, Class)
	 * @see #forModel(List, BiFunction) 
	 */
	public static <IN, OUT> AccessorChain<IN, OUT> forModel(List<IAccessor> accessors) {
		return forModel(accessors, null);
	}
	
	/**
	 * Creates a chain that :
	 * - returns null when any accessor on path returns null
	 * - initializes values (instanciate bean) on path when its mutator is used
	 * (voluntary dissimetric behavior)
	 * 
	 * @param accessors list of {@link IAccessor} to be used by chain
	 * @param valueTypeDeterminer must be given if a bean type is badly determined by default mecanism
	 * 		  (returning Object on generic for instance, or wrong Collection concrete type), null accepted (means default mecanism)
	 * @see #RETURN_NULL
	 * @see ValueInitializerOnNullValue#giveValueType(IAccessor, Class)
	 */
	public static <IN, OUT> AccessorChain<IN, OUT> forModel(List<IAccessor> accessors, @Nullable BiFunction<IAccessor, Class, Class> valueTypeDeterminer) {
		return new AccessorChain<IN, OUT>(accessors) {
			
			private final AccessorChainMutator<IN, Object, OUT> mutator = (AccessorChainMutator<IN, Object, OUT>) super.toMutator()
					.setNullValueHandler(new ValueInitializerOnNullValue(valueTypeDeterminer));
			
			@Override
			public AccessorChainMutator toMutator() {
				return mutator;
			}
		}.setNullValueHandler(AccessorChain.RETURN_NULL);
	}
	
	/**
	 * Will throw a {@link NullPointerException} if a link in an accessor chain returns null.
	 * Default behavior
	 */
	public static final NullValueHandler THROW_NULLPOINTEREXCEPTION = new NullPointerExceptionThrowerOnNullValue();
	
	/** Will return null if a link in an accessor chain returns null */
	public static final NullValueHandler RETURN_NULL = new NullReturnerOnNullValue();
	
	/** Will instanciate needed value (and set it) if a link in an accessor chain returns null */
	public static final NullValueHandler INITIALIZE_VALUE = new ValueInitializerOnNullValue();
	
	private final List<IAccessor> accessors;
	
	private NullValueHandler nullValueHandler = THROW_NULLPOINTEREXCEPTION;
	
	public AccessorChain() {
		this(new ArrayList<>(5));
	}
	
	public AccessorChain(IAccessor ... accessors) {
		this(Arrays.asList(accessors));
	}
	
	public AccessorChain(List<IAccessor> accessors) {
		this.accessors = accessors;
	}
	
	public List<IAccessor> getAccessors() {
		return accessors;
	}
	
	public void add(IAccessor accessor) {
		accessors.add(accessor);
	}
	
	public void add(IAccessor ... accessors) {
		add(Arrays.asList(accessors));
	}
	
	public void add(Iterable<IAccessor> accessors) {
		this.accessors.addAll((Collection<IAccessor>) accessors);
	}
	
	public AccessorChain<C, T> setNullValueHandler(NullValueHandler nullValueHandler) {
		this.nullValueHandler = nullValueHandler;
		return this;
	}
	
	@Override
	public T doGet(C c) {
		Object target = c;
		Object previousTarget;
		for (IAccessor accessor : accessors) {
			previousTarget = target;
			target = accessor.get(target);
			if (target == null) {
				Object handlerResult = onNullValue(previousTarget, accessor);
				if (handlerResult == null) {
					// we must go out from the loop to avoid a NullPointerException, moreover it has no purpose to continue iteration
					return null;
				} else {
					target = handlerResult;
				}
			}
		}
		return (T) target;
	}
	
	/**
	 * Method called when a null value is returned by an accessor in the chain
	 * @param targetBean bean on which accessor was invoked
	 * @param accessor accessor that returned null when invoked on targetBean
	 * @return the value that should replace null value, can be null too
	 */
	@Nullable
	protected Object onNullValue(Object targetBean, IAccessor accessor) {
		return this.nullValueHandler.consume(targetBean, accessor);
	}
	
	/**
	 * Only supported when last accessor is reversible (aka implements {@link IReversibleAccessor}.
	 *
	 * @return a new chain which path is the same as this
	 * @throws UnsupportedOperationException if last accessor is not reversible
	 */
	@Override
	public AccessorChainMutator<C, Object, T> toMutator() {
		IAccessor lastAccessor = Iterables.last(getAccessors());
		if (lastAccessor instanceof IReversibleAccessor) {
			IReversibleMutator<Object, T> lastMutator = (IReversibleMutator<Object, T>) ((IReversibleAccessor) lastAccessor).toMutator();
			return new AccessorChainMutator<>(Collections.cutTail(getAccessors()), lastMutator);
		} else {
			throw new UnsupportedOperationException("Last accessor cannot be reverted because it's not " + IReversibleAccessor.class.getName()
					+ ": " + lastAccessor);
		}
	}
	
	@Override
	public boolean equals(Object other) {
		return this == other || (other instanceof AccessorChain && accessors.equals(((AccessorChain) other).accessors));
	}
	
	@Override
	public int hashCode() {
		return accessors.hashCode();
	}
	
	@Override
	protected String getGetterDescription() {
		return accessors.toString();
	}
	
	/**
	 * Contract for handling null objects during accessor chaining
	 */
	public interface NullValueHandler {
		
		Object consume(Object srcBean, IAccessor accessor);
		
	}
	
	/**
	 * Class that will throw a {@link NullPointerException} when a null value is encountered
	 */
	private static class NullPointerExceptionThrowerOnNullValue implements NullValueHandler {
		@Override
		public Object consume(Object srcBean, IAccessor accessor) {
			String accessorDescription = accessor.toString();
			String exceptionMessage;
			if (accessor instanceof AccessorByField) {
				exceptionMessage = srcBean + " has null value on field " + ((AccessorByField) accessor).getGetter().getName();
			} else {
				exceptionMessage = "Call of " + accessorDescription + " on " + srcBean + " returned null";
			}
			throw new NullPointerException(exceptionMessage);
		}
	}
	
	/**
	 * Simple class that will always return null for the whole chain when a null value is encountered
	 */
	private static class NullReturnerOnNullValue implements NullValueHandler {
		@Override
		public Object consume(Object srcBean, IAccessor accessor) {
			return null;
		}
	}
	
	/**
	 * Class that will initialize value by instanciating its class and set it onto the property.
	 * Instanciated types can be controlled through {@link #giveValueType(IAccessor, Class)}.
	 */
	public static class ValueInitializerOnNullValue implements NullValueHandler {
		
		private final BiFunction<IAccessor, Class, Class> valueTypeDeterminer;
		
		public ValueInitializerOnNullValue() {
			this(null);
		}
		
		public ValueInitializerOnNullValue(@Nullable BiFunction<IAccessor, Class, Class> valueTypeDeterminer) {
			this.valueTypeDeterminer = Objects.preventNull(valueTypeDeterminer, ValueInitializerOnNullValue::giveValueType);
		}
		
		@Override
		public Object consume(Object srcBean, IAccessor accessor) {
			if (accessor instanceof IReversibleAccessor) {
				IMutator mutator = ((IReversibleAccessor) accessor).toMutator();
				Class inputType = giveInputType(mutator);
				// NB: will throw an exception if type is not instanciable
				Object value = Reflections.newInstance(valueTypeDeterminer.apply(accessor, inputType));
				mutator.set(srcBean, value);
				return value;
			} else {
				throw new UnsupportedOperationException(
						"accessor cannot be reverted because it's not " + Reflections.toString(IReversibleAccessor.class) + ": " + accessor);
			}
		}
		
		/**
		 * Expected to give concrete class to be instanciated.
		 * @param accessor the current accessor that returned null, given for a fine grained adjustment of returned type
		 * @param valueType expected compatible type, this of accessor
		 * @return a concrete and instanciable type compatible with acccessor input type
		 */
		public static Class giveValueType(IAccessor accessor, Class valueType) {
			if (List.class.equals(valueType)) {
				return ArrayList.class;
			} else if (Set.class.equals(valueType)) {
				return HashSet.class;
			} else if (Map.class.equals(valueType)) {
				return HashMap.class;
			} else {
				return valueType;
			}
		}
	}
	
	
}
