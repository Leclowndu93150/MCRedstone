package com.leclowndu93150.mcredstone.compiler.route;

import java.util.Arrays;

final class Heap {
    private int[] keys = new int[1024];
    private float[] priorities = new float[1024];
    private int size;

    void clear() {
        size = 0;
    }

    boolean isEmpty() {
        return size == 0;
    }

    float peekPriority() {
        return priorities[0];
    }

    void push(int key, float priority) {
        if (size == keys.length) {
            keys = Arrays.copyOf(keys, size * 2);
            priorities = Arrays.copyOf(priorities, size * 2);
        }
        int i = size++;
        while (i > 0) {
            int up = (i - 1) / 2;
            if (priorities[up] <= priority) {
                break;
            }
            keys[i] = keys[up];
            priorities[i] = priorities[up];
            i = up;
        }
        keys[i] = key;
        priorities[i] = priority;
    }

    int pop() {
        int result = keys[0];
        int lastKey = keys[--size];
        float lastPriority = priorities[size];
        int i = 0;
        while (true) {
            int child = 2 * i + 1;
            if (child >= size) {
                break;
            }
            if (child + 1 < size && priorities[child + 1] < priorities[child]) {
                child++;
            }
            if (priorities[child] >= lastPriority) {
                break;
            }
            keys[i] = keys[child];
            priorities[i] = priorities[child];
            i = child;
        }
        keys[i] = lastKey;
        priorities[i] = lastPriority;
        return result;
    }
}
