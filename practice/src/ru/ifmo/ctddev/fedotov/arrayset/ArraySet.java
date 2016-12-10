package ru.ifmo.ctddev.fedotov.arrayset;


import java.util.*;
import java.util.SortedSet;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

public class ArraySet<E> extends AbstractSet<E> implements SortedSet<E> {
    private static final String ERROR_EMPTY_SET = "Empty set!";
    private final Object[] objects;
    private final Comparator<? super E> comparator;

    public ArraySet() {
        this.objects = new Object[0];
        comparator = null;

    }

    public ArraySet(final Collection<? extends E> source) {
        this.objects = source.stream().sorted().distinct().toArray();
        comparator = null;
    }

    private ArraySet(final E[] source, final int fromInclusive, final int toExclusive, final Comparator<? super E> comparator) {
        this.objects = Arrays.copyOfRange(source, fromInclusive, toExclusive);
        this.comparator = comparator;
    }

    public ArraySet(final Collection<? extends E> source, final Comparator<? super E> comparator) {
        this.comparator = comparator;
        this.objects = source.stream()
                .collect(Collectors.toCollection(() -> new TreeSet<E>(comparator)))
                .stream()
                .toArray();
    }

    private int binSearch(final E key) {
        return comparator == null
                ? Arrays.binarySearch(objects, key)
                : Arrays.binarySearch((E[]) objects, key, comparator);
    }

    @Override
    public SortedSet<E> subSet(final E fromElement, final E toElement) {
        return subSetByPositions(searchFromPosition(fromElement), searchToPosition(toElement));
    }

    private int searchToPosition(final E key) {
        final int position = binSearch(key);
        if (position == 0) {
            return -1;
        } else if (position < 0) {
            final int insertionPoint = -position - 1;
            return insertionPoint == 0 ? -1 : insertionPoint - 1;
        } else {
            return position - 1;
        }
    }

    public int searchFromPosition(final E key) {
        final int position = binSearch(key);
        if (position < 0) {
            final int insertionPoint = -position - 1;
            return insertionPoint == objects.length ? -1 : insertionPoint;
        } else {
            return position;
        }
    }

    private SortedSet<E> subSetByPositions(final int fromElement, final int toElement) {
        if (fromElement == -1 || toElement == -1 || toElement < fromElement) {
            return new ArraySet<>();
        }
        return new ArraySet<>((E[]) objects, fromElement, toElement + 1, this.comparator);
    }

    @Override
    public SortedSet<E> headSet(final E toElement) {
        final int fromPosition = 0;
        return subSetByPositions(fromPosition, searchToPosition(toElement));
    }

    @Override
    public SortedSet<E> tailSet(final E fromElement) {
        final int toPosition = objects.length - 1 < 0 ? 0 : objects.length - 1;
        return subSetByPositions(searchFromPosition(fromElement), toPosition);
    }

    @Override
    public Iterator<E> iterator() {
        IntUnaryOperator unaryOperator = operand -> operand + 1;
        return new IteratorSortedSet(unaryOperator, objects.length);
    }

    @Override
    public int size() {
        return objects.length;
    }

    @Override
    public boolean contains(final Object o) {
        return binSearch((E) o) >= 0;
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        for (Object o : c) {
            if (binSearch((E) o) < 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public E first() {
        if (objects.length == 0) {
            throw new NoSuchElementException(ERROR_EMPTY_SET);
        } else {
            return (E) objects[0];
        }
    }

    @Override
    public E last() {
        if (objects.length == 0) {
            throw new NoSuchElementException(ERROR_EMPTY_SET);
        } else {
            return (E) objects[objects.length - 1];
        }
    }

    private class IteratorSortedSet implements Iterator<E> {
        private final IntUnaryOperator operator;
        private final int end;
        private int position;

        IteratorSortedSet(final IntUnaryOperator operator, final int end) {
            this.operator = operator;
            this.end = end;
        }

        @Override
        public boolean hasNext() {
            return position != end;
        }

        @Override
        public E next() {
            if (hasNext()) {
                final E result = (E) objects[position];
                position = operator.applyAsInt(position);
                return result;
            } else {
                throw new NoSuchElementException("No more elements!");
            }
        }
    }
}