package de.bottlecaps.markup.blitz.transform;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;

import de.bottlecaps.markup.blitz.character.Range;

public interface TileIterator {
  public int next(int[] target, int offset);
  public int numberOfTiles();
  public int tileSize();

//  public static TileIterator of(NavigableMap<Range, Integer> terminalCodeByRange, int tileIndexBits) {
//    return of(terminalCodeByRange, tileIndexBits, Integer.MAX_VALUE);
//  }

  public static TileIterator of(NavigableMap<Range, Integer> terminalCodeByRange, int tileIndexBits, int maxCodepoint) {
    return new TileIterator() {
      int defaultValue = 0;
      int tileSize = 1 << tileIndexBits;
      int numberOfTiles;

      Iterator<Map.Entry<Range, Integer>> it;
      int currentCp;

      Range currentRange;
      int firstCp;
      int lastCp;
      int tokenCode;

      {
        if (terminalCodeByRange.isEmpty()) {
          firstCp = -1;
          lastCp = -1;
        }
        else {
          it = terminalCodeByRange.entrySet().iterator();
          nextRange();
        }
        numberOfTiles = (maxCodepoint + tileSize) / tileSize;
        currentCp = 0;
      }

      @Override
      public int numberOfTiles() {
        return numberOfTiles;
      }

      @Override
      public int tileSize() {
        return tileSize;
      }

      @Override
      public int next(int[] target, int offset) {
        if (currentCp < firstCp - tileSize)
          return many(target, offset, firstCp - currentCp, defaultValue);
        if (currentCp >= firstCp && currentCp <= lastCp - tileSize) {
          int result = many(target, offset, lastCp - currentCp + 1, tokenCode);
          if (currentCp > lastCp)
            nextRange();
          return result;
        }
        for (int size = 0;; nextRange()) {
          if (firstCp < 0) {
            if (size != 0) {
              Arrays.fill(target, offset + size, offset + tileSize, defaultValue);
              currentCp += tileSize - size;
              return 1;
            }
            return many(target, offset, numberOfTiles * tileSize - currentCp, defaultValue);
          }
          while (currentCp < firstCp) {
            ++currentCp;
            target[offset + size++] = defaultValue;
            if (size == tileSize)
              return 1;
          }
          while (currentCp <= lastCp) {
            ++currentCp;
            target[offset + size++] = tokenCode;
            if (size == tileSize) {
              if (currentCp > lastCp)
                nextRange();
              return 1;
            }
          }
        }
      }

      private void nextRange() {
        if (! it.hasNext()) {
          firstCp = -1;
          lastCp = -1;
        }
        else {
          Map.Entry<Range, Integer> entry = it.next();
          currentRange = entry.getKey();
          firstCp = currentRange.getFirstCodepoint();
          if (firstCp > maxCodepoint) {
            firstCp = -1;
            lastCp = -1;
          }
          else {
            tokenCode = entry.getValue();
            lastCp = currentRange.getLastCodepoint();
            if (lastCp > maxCodepoint)
              lastCp = maxCodepoint;
          }
        }
      }

      private int many(int[] target, int offset, int n, int value) {
        for (int i = 0; i < tileSize; ++i)
          target[offset + i] = value;
        int nt = n / tileSize;
        currentCp += nt * tileSize;
        return nt;
      }
    };
  }

  public static TileIterator of(int[] array, int tileIndexBits) {
    return new TileIterator() {
      int defaultValue = 0;
      int tileSize = 1 << tileIndexBits;
      int numberOfTiles = (array.length + tileSize - 1) / tileSize;
      int nextOffset = 0;

      @Override
      public int next(int[] target, int targetOffset) {
        int remainingSize = array.length - nextOffset;
        if (remainingSize <= 0)
          return 0;
        if (remainingSize < tileSize) {
          System.arraycopy(array, nextOffset, target, targetOffset, remainingSize);
          Arrays.fill(target, targetOffset + remainingSize, targetOffset + tileSize, defaultValue);
          nextOffset += remainingSize;
          return 1;
        }
        System.arraycopy(array, nextOffset, target, targetOffset, tileSize);
        int count = 1;
        nextOffset += tileSize;
        while (array.length - nextOffset >= tileSize
            && 0 == Arrays.compare(
              target, targetOffset, targetOffset + tileSize,
              array, nextOffset, nextOffset + tileSize)) {
          ++count;
          nextOffset += tileSize;
        }
        return count;
      }

      @Override
      public int numberOfTiles() {
        return numberOfTiles;
      }

      @Override
      public int tileSize() {
        return tileSize;
      }
    };
  }
}
