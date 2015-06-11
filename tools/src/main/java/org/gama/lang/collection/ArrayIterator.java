package org.gama.lang.collection;

/**
 * Wraps an array into an Iterator. Allows reuse of an array in the Iterators API.
 * 
 * @author Guillaume Mary
 */
public class ArrayIterator<O> extends ReadOnlyIterator<O> {

	private O[] array;
	private int currentIndex = 0, maxIndex;

	public ArrayIterator(O[] array) {
		this.array = array;
		this.maxIndex = array.length;
	}

	@Override
	public boolean hasNext() {
		return currentIndex < maxIndex;
	}

	@Override
	protected O getNext() {
		return this.array[currentIndex++];
	}
}
