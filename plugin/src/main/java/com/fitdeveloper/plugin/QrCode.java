package com.fitdeveloper.plugin;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Self-contained QR-code model — a compact byte-mode encoder (ECC levels
 * L/M, versions 1-10, all 8 masks with automatic penalty selection).
 *
 * Why hand-rolled: the native control panel needs a scannable QR for the
 * phone walker, but Android Studio ships without JCEF (no browser, no
 * JavaScript QR library) and the plugin must stay dependency-free. This
 * class renders the matrix into any Swing component in ~1 ms.
 *
 * Scope is deliberately tiny: the plugin only ever encodes a URL
 * ("https://192.168.x.x:8791/walk?s=abc1234567" - at most a few dozen
 * bytes), which fits version 3-4; version 10 (213 bytes at ECC M) is a
 * generous ceiling. Anything longer throws.
 *
 * The implementation follows the ISO/IEC 18004 core algorithm
 * (Reed-Solomon ECC over GF(2^8), zigzag placement, BCH format/version
 * bits, 8 masks scored by the standard penalty rules). The output was
 * verified with independent decoders.
 */
public final class QrCode {

    /** Error-correction level (only the two practical ones are offered). */
    public enum Ecc {
        L(1),  // ~7% recoverable
        M(0);  // ~15% recoverable — the default for screen-rendered codes

        final int formatBits;

        Ecc(int formatBits) {
            this.formatBits = formatBits;
        }
    }

    /** Chosen version (1..10). Modules per side = version * 4 + 17. */
    public final int version;
    /** Side length in modules (version * 4 + 17), quiet zone NOT included. */
    public final int size;
    /** modules[y][x] == true means a dark module (no quiet zone included). */
    public final boolean[][] modules;
    /** Marks function-pattern modules (excluded from masking/data). */
    private final boolean[][] isFunction;
    /** Format-bits value of the chosen ECC level (L=1, M=0). */
    private final int eccFormatBits;

    private static final int MIN_VERSION = 1;
    private static final int MAX_VERSION = 10;

    /** ECC codewords per block, indexed [eccOrdinal][version-1]. */
    private static final int[][] ECC_CODEWORDS_PER_BLOCK = {
            {7, 10, 15, 20, 26, 18, 20, 24, 30, 18},  // Low
            {10, 16, 26, 18, 24, 16, 18, 22, 22, 26}, // Medium
    };
    /** Number of Reed-Solomon blocks, indexed [eccOrdinal][version-1]. */
    private static final int[][] NUM_ECC_BLOCKS = {
            {1, 1, 1, 1, 1, 2, 2, 2, 2, 4},  // Low
            {1, 1, 1, 2, 2, 4, 4, 4, 5, 5},  // Medium
    };

    /** Builds a QR for the given text at ECC level M (auto version, auto mask). */
    public QrCode(String text) {
        this(text, Ecc.M);
    }

    /** Builds a QR for the given UTF-8 text (auto version, auto mask). */
    public QrCode(String text, Ecc ecc) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int ver = -1;
        for (int v = MIN_VERSION; v <= MAX_VERSION; v++) {
            if (data.length <= byteCapacity(v, ecc)) {
                ver = v;
                break;
            }
        }
        if (ver < 0) {
            throw new IllegalArgumentException("text too long: " + data.length
                    + " bytes (max " + byteCapacity(MAX_VERSION, ecc) + " at ECC "
                    + ecc + ", version " + MAX_VERSION + ")");
        }
        version = ver;
        size = ver * 4 + 17;
        modules = new boolean[size][size];
        isFunction = new boolean[size][size];
        eccFormatBits = ecc.formatBits;

        drawFunctionPatterns(ver);
        byte[] allCodewords = encodeCodewords(data, ver, ecc);
        drawBestMask(allCodewords, ecc);
    }

    // ---------------- encoding ----------------

    /** Max byte-mode payload for a version/ECC pair (v1-9 use an 8-bit count). */
    private static int byteCapacity(int ver, Ecc ecc) {
        int dataCodewords = getNumDataCodewords(ver, ecc);
        int countBits = ver < 10 ? 8 : 16;
        return (dataCodewords * 8 - 4 - countBits) / 8;
    }

    private byte[] encodeCodewords(byte[] data, int ver, Ecc ecc) {
        int dataCodewords = getNumDataCodewords(ver, ecc);
        BitBuf bits = new BitBuf(dataCodewords * 8);
        bits.append(0b0100, 4); // byte-mode indicator
        bits.append(data.length, ver < 10 ? 8 : 16);
        for (byte b : data) {
            bits.append(b & 0xFF, 8);
        }
        // terminator (up to 4 zero bits), pad to byte, pad codewords 0xEC/0x11
        bits.append(0, Math.min(4, bits.cap() - bits.len));
        bits.append(0, (8 - bits.len % 8) % 8);
        for (int pad = 0xEC; bits.len < bits.cap(); pad ^= 0xEC ^ 0x11) {
            bits.append(pad, 8);
        }
        byte[] dataCodewordsArr = new byte[dataCodewords];
        for (int i = 0; i < dataCodewords; i++) {
            int b = 0;
            for (int j = 0; j < 8; j++) {
                b = (b << 1) | (bits.bits[i * 8 + j] ? 1 : 0);
            }
            dataCodewordsArr[i] = (byte) b;
        }
        return addEccAndInterleave(dataCodewordsArr, ver, ecc);
    }

    /** Fixed-capacity bit buffer (total bits are known before encoding). */
    private static final class BitBuf {
        final boolean[] bits;
        int len;

        BitBuf(int capBits) {
            bits = new boolean[capBits];
        }

        int cap() {
            return bits.length;
        }

        void append(int val, int n) {
            for (int i = n - 1; i >= 0; i--) {
                bits[len++] = ((val >>> i) & 1) != 0;
            }
        }
    }

    // ---------------- function patterns ----------------

    private void drawFunctionPatterns(int ver) {
        // finders + separators (x, y = center of the 7x7 pattern)
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        // timing
        for (int i = 8; i < size - 8; i++) {
            boolean dark = i % 2 == 0;
            setFunction(i, 6, dark);
            setFunction(6, i, dark);
        }
        // alignment
        int[] pos = alignmentPositions(ver);
        for (int i = 0; i < pos.length; i++) {
            for (int j = 0; j < pos.length; j++) {
                if (pos[i] == 6 && pos[j] == 6) {
                    continue; // top-left finder
                }
                if (pos[i] == size - 7 && pos[j] == 6) {
                    continue; // top-right finder
                }
                if (pos[i] == 6 && pos[j] == size - 7) {
                    continue; // bottom-left finder
                }
                drawAlignment(pos[i], pos[j]);
            }
        }
        drawFormatBits(0); // placeholder; rewritten for the chosen mask
        drawVersion(ver);
    }

    /** 7x7 finder + light separator, centered at (x, y). */
    private void drawFinder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx;
                int yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) {
                    setFunction(xx, yy, dist != 2 && dist != 4);
                }
            }
        }
    }

    private void drawAlignment(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private static int[] alignmentPositions(int ver) {
        if (ver == 1) {
            return new int[0];
        }
        int numAlign = ver / 7 + 2;
        int step = (ver * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        int side = ver * 4 + 17;
        for (int i = 0, p = side - 7; i < numAlign - 1; i++, p -= step) {
            result[numAlign - 1 - i] = p;
        }
        return result;
    }

    private void drawFormatBits(int mask) {
        int data = eccFormatBits << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = (data << 10 | rem) ^ 0x5412;
        // first copy (around top-left finder)
        for (int i = 0; i <= 5; i++) {
            setFunction(8, i, getBit(bits, i));
        }
        setFunction(8, 7, getBit(bits, 6));
        setFunction(8, 8, getBit(bits, 7));
        setFunction(7, 8, getBit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunction(14 - i, 8, getBit(bits, i));
        }
        // second copy (split between the other two finders)
        for (int i = 0; i < 8; i++) {
            setFunction(size - 1 - i, 8, getBit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunction(8, size - 15 + i, getBit(bits, i));
        }
        setFunction(8, size - 8, true); // always-dark module
    }

    private void drawVersion(int ver) {
        if (ver < 7) {
            return;
        }
        int rem = ver;
        for (int i = 0; i < 12; i++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        }
        int bits = ver << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean bit = getBit(bits, i);
            int a = size - 11 + i % 3;
            int b = i / 3;
            setFunction(a, b, bit);
            setFunction(b, a, bit);
        }
    }

    // ---------------- data placement + masking ----------------

    private void drawBestMask(byte[] codewords, Ecc ecc) {
        int best = 0;
        long bestPenalty = Long.MAX_VALUE;
        boolean[][] bestMatrix = new boolean[size][size];
        for (int mask = 0; mask < 8; mask++) {
            // reset data area
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (!isFunction[y][x]) {
                        modules[y][x] = false;
                    }
                }
            }
            drawCodewords(codewords);
            applyMask(mask);
            drawFormatBits(mask);
            long penalty = maskPenalty();
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = mask;
                for (int y = 0; y < size; y++) {
                    bestMatrix[y] = modules[y].clone();
                }
            }
        }
        for (int y = 0; y < size; y++) {
            modules[y] = bestMatrix[y];
        }
    }

    private void drawCodewords(byte[] data) {
        int bitLen = data.length * 8;
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < bitLen) {
                        modules[y][x] = getBit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!isFunction[y][x] && maskBit(mask, x, y)) {
                    modules[y][x] ^= true;
                }
            }
        }
    }

    private static boolean maskBit(int mask, int x, int y) {
        switch (mask) {
            case 0:  return (x + y) % 2 == 0;
            case 1:  return y % 2 == 0;
            case 2:  return x % 3 == 0;
            case 3:  return (x + y) % 3 == 0;
            case 4:  return (x / 3 + y / 2) % 2 == 0;
            case 5:  return x * y % 2 + x * y % 3 == 0;
            case 6:  return (x * y % 2 + x * y % 3) % 2 == 0;
            case 7:  return ((x + y) % 2 + x * y % 3) % 2 == 0;
            default: throw new IllegalArgumentException("mask " + mask);
        }
    }

    /** Standard penalty (rules N1, N2, N3, N4) — lower is better. */
    private long maskPenalty() {
        long result = 0;
        // N1: runs of >=5 same-colored modules in rows and columns
        for (int y = 0; y < size; y++) {
            boolean color = modules[y][0];
            int run = 1;
            for (int x = 1; x < size; x++) {
                if (modules[y][x] == color) {
                    run++;
                    if (run == 5) {
                        result += 3;
                    } else if (run > 5) {
                        result++;
                    }
                } else {
                    color = modules[y][x];
                    run = 1;
                }
            }
        }
        for (int x = 0; x < size; x++) {
            boolean color = modules[0][x];
            int run = 1;
            for (int y = 1; y < size; y++) {
                if (modules[y][x] == color) {
                    run++;
                    if (run == 5) {
                        result += 3;
                    } else if (run > 5) {
                        result++;
                    }
                } else {
                    color = modules[y][x];
                    run = 1;
                }
            }
        }
        // N2: every 2x2 same-colored block
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    result += 3;
                }
            }
        }
        // N3: finder-like 1011101 patterns with 4 light modules on a side
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size - 6; x++) {
                if (finderLike(modules[y], x)) {
                    result += 40;
                }
            }
        }
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size - 6; y++) {
                if (finderLikeCol(x, y)) {
                    result += 40;
                }
            }
        }
        // N4: dark/light balance
        int dark = 0;
        for (boolean[] row : modules) {
            for (boolean m : row) {
                if (m) {
                    dark++;
                }
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += k * 10L;
        return result;
    }

    /** 1011101 with at least 4 light modules on one side (row variant). */
    private boolean finderLike(boolean[] row, int x) {
        return row[x] && !row[x + 1] && row[x + 2] && row[x + 3] && row[x + 4]
                && !row[x + 5] && row[x + 6]
                && (lightRun(row, x - 4, x) || lightRun(row, x + 7, x + 11));
    }

    private boolean finderLikeCol(int x, int y) {
        return modules[y][x] && !modules[y + 1][x] && modules[y + 2][x]
                && modules[y + 3][x] && modules[y + 4][x] && !modules[y + 5][x]
                && modules[y + 6][x]
                && (lightRunCol(x, y - 4, y) || lightRunCol(x, y + 7, y + 11));
    }

    private boolean lightRun(boolean[] row, int from, int to) {
        if (from < 0 || to > row.length) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (row[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean lightRunCol(int x, int from, int to) {
        if (from < 0 || to > size) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (modules[i][x]) {
                return false;
            }
        }
        return true;
    }

    // ---------------- Reed-Solomon over GF(2^8) ----------------

    private byte[] addEccAndInterleave(byte[] data, int ver, Ecc ecc) {
        int ord = ecc.ordinal();
        int numBlocks = NUM_ECC_BLOCKS[ord][ver - 1];
        int blockEccLen = ECC_CODEWORDS_PER_BLOCK[ord][ver - 1];
        int rawCodewords = getNumRawDataModules(ver) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;

        byte[][] blocks = new byte[numBlocks][];
        byte[] divisor = rsDivisor(blockEccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int datLen = shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1);
            byte[] dat = Arrays.copyOfRange(data, k, k + datLen);
            k += datLen;
            byte[] eccArr = rsRemainder(dat, divisor);
            if (i < numShortBlocks) {
                dat = Arrays.copyOf(dat, dat.length + 1); // pad slot for interleave
            }
            byte[] block = new byte[dat.length + eccArr.length];
            System.arraycopy(dat, 0, block, 0, dat.length);
            System.arraycopy(eccArr, 0, block, dat.length, eccArr.length);
            blocks[i] = block;
        }
        byte[] result = new byte[rawCodewords];
        int j = 0;
        for (int i = 0; i < blocks[0].length; i++) {
            for (int b = 0; b < numBlocks; b++) {
                // skip the pad slot in short blocks
                if (i != shortBlockLen - blockEccLen || b >= numShortBlocks) {
                    result[j++] = blocks[b][i];
                }
            }
        }
        return result;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int jj = 0; jj < result.length; jj++) {
                result[jj] = (byte) rsMultiply(result[jj] & 0xFF, root);
                if (jj + 1 < result.length) {
                    result[jj] ^= result[jj + 1] & 0xFF;
                }
            }
            root = rsMultiply(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b & 0xFF) ^ (result[0] & 0xFF);
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < divisor.length; i++) {
                result[i] ^= rsMultiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int rsMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    // ---------------- geometry helpers ----------------

    private static int getNumRawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    private static int getNumDataCodewords(int ver, Ecc ecc) {
        return getNumRawDataModules(ver) / 8
                - ECC_CODEWORDS_PER_BLOCK[ecc.ordinal()][ver - 1]
                * NUM_ECC_BLOCKS[ecc.ordinal()][ver - 1];
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private static boolean getBit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }
}
