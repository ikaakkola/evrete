package org.evrete.spi.minimal;

import org.evrete.api.*;
import org.evrete.collections.FastHashSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

class SharedBetaData implements SharedBetaFactStorage {
    private final Object[] reusableValueArr;
    private final FieldsFactMap deltaNewKeys = new FieldsFactMap();
    private final FieldsFactMap deltaKnownKeys = new FieldsFactMap();
    private final FieldsFactMap main = new FieldsFactMap();
    private final FastHashSet<ValueRow> deleteTasks = new FastHashSet<>();
    private final ActiveField[] fields;
    private final EnumMap<KeyMode, ReIterable<ValueRow>> keyIterables;

    private final BiPredicate<ValueRowImpl, Object[]> SHARED_ARRAY_EQ = new BiPredicate<ValueRowImpl, Object[]>() {
        @Override
        public boolean test(ValueRowImpl entry, Object[] values) {
            return MiscUtils.sameData(entry.data, reusableValueArr);
        }
    };

    SharedBetaData(FieldsKey typeFields) {
        this.fields = typeFields.getFields();
        this.reusableValueArr = new Object[fields.length];
        this.keyIterables = buildKeyIterables();
    }


    @Override
    public EnumMap<KeyMode, ReIterable<ValueRow>> keyIterables() {
        return keyIterables;
    }

    private EnumMap<KeyMode, ReIterable<ValueRow>> buildKeyIterables() {
        EnumMap<KeyMode, ReIterable<ValueRow>> map = new EnumMap<>(KeyMode.class);
        for(KeyMode mode : KeyMode.values()) {
            ReIterable<ValueRow> iterator;
            switch (mode) {
                case KNOWN_KEYS_KNOWN_FACTS:
                    iterator = main::keyIterator;
                    break;
                case KNOWN_KEYS_NEW_FACTS:
                    iterator = deltaKnownKeys::keyIterator;
                    break;
                case NEW_KEYS_NEW_FACTS:
                    iterator = deltaNewKeys::keyIterator;
                    break;
                default:
                    throw new IllegalStateException();
            }
            map.put(mode, iterator);
        }
        return map;
    }

    @Override
    public boolean hasDeletedKeys() {
        return deleteTasks.size() > 0;
    }

    @Override
    public boolean isKeyDeleted(ValueRow row) {
        return deleteTasks.contains(row);
    }

    @Override
    public void clear() {
        deltaNewKeys.clear();
        deltaKnownKeys.clear();
        main.clear();
        deleteTasks.clear();
    }

    @Override
    public void clearDeletedKeys() {
        deleteTasks.clear();
    }

    @ThreadUnsafe
    private int hash(FieldToValue key) {
        int hash = 0;
        for (int i = 0; i < fields.length; i++) {
            hash ^= (reusableValueArr[i] = key.apply(fields[i])).hashCode();
        }
        return hash;
    }

    @Override
    public void ensureDeltaCapacity(int insertCount) {
        deltaNewKeys.resize(deltaNewKeys.size() + insertCount);
        deltaKnownKeys.resize(deltaKnownKeys.size() + insertCount);
    }

    private void delete(RuntimeFact fact) {
        int hash = hash(fact);
        int addr = main.findBinIndex(reusableValueArr, hash, SHARED_ARRAY_EQ);
        ValueRow deleted = main.deleteAndTestExisting(fact, addr);
        if (deleted != null) {
            deleteTasks.add(deleted);
        }
    }

    @Override
    public void delete(Collection<? extends RuntimeFact> collection, Predicate<RuntimeFact> predicate) {
        for(RuntimeFact fact : collection) {
            if(predicate.test(fact)) {
                delete(fact);
            }
        }
    }

    @Override
    public void commitChanges() {
        main.addAll(deltaNewKeys);
        main.addAll(deltaKnownKeys);

        deltaNewKeys.clear();
        deltaKnownKeys.clear();

        clearDeletedKeys();

    }

    private void insert(RuntimeFact fact) {
        int hash = hash(fact);
        int addr = main.findBinIndex(reusableValueArr, hash, SHARED_ARRAY_EQ);
        if (main.get(addr) == null) {
            // No entry in main storage, now looking in delta
            insertTo(hash, fact, deltaNewKeys);
        } else {
            // The key is already known to the main storage, yet we need to keep a copy of inserts for RHS calls
            // see https://github.com/andbi/evrete/issues/2
            insertTo(hash, fact, deltaKnownKeys);
        }
    }

    private void insertTo(int hash, RuntimeFact fact, FieldsFactMap destination) {
        int addr = destination.findBinIndex(reusableValueArr, hash, SHARED_ARRAY_EQ);
        ValueRowImpl key = destination.get(addr);
        if (key == null) {
            ValueRowImpl vr = new ValueRowImpl(Arrays.copyOf(reusableValueArr, reusableValueArr.length), hash, fact);
            destination.saveDirect(vr, addr);
        } else {
            key.addFact(fact);
        }
    }


    @Override
    public void insert(Collection<? extends RuntimeFact> collection, Predicate<RuntimeFact> predicate) {
        ensureDeltaCapacity(collection.size());
        for (RuntimeFact fact : collection) {
            if (predicate.test(fact)) {
                insert(fact);
            }
        }
    }
}
