#!/usr/bin/python

import numpy as np

LED_SEQUENCE = [
    "VIOLET",        # 0
    "ROYAL BLUE",
    "BLUE",
    "CYAN",
    "GREEN",
    "LIME",          # 5
    "AMBER",
    "RED ORANGE",
    "RED",
    "DEEP RED",
    "FAR RED",       # 10
    "BLACK",
    "WHITE"
]


def calc_num_rows(ton, toff, size):

    denom = (ton + toff) / 5.0
    denom = denom if denom > 1 else 1

    return (int)(size/denom)


def consecutive(x, stepsize=3):
    return np.split(x, np.where(np.diff(x) > stepsize)[0]+1)


def one_hot_encode(x):
    x = np.ravel(np.array(x).astype(int)) + 1
    one_hot = np.zeros((x.size, x.max()+1))
    one_hot[np.arange(x.size), x] = 1

    # Lazy zero blacks (black is index 12) and drop first row (zeros)
    one_hot = one_hot.T[1:-1].T

    return one_hot


def make_color_map(seq, n, v=False):

    col_seq = np.ravel(seq[0][:n])

    col_seq = np.insert(col_seq, 0, LED_SEQUENCE.index("BLUE"))
    col_seq = np.append(col_seq, LED_SEQUENCE.index("RED"))

    col_seq = np.insert(col_seq, 0, LED_SEQUENCE.index("BLACK"))
    col_seq = np.append(col_seq, LED_SEQUENCE.index("BLACK"))

    if v:
        print("Color sequence:")
        print(col_seq)
        print(f"Length: {len(col_seq)}")

    return col_seq


def extend_color_map(color_map, row_span, black_mul):

    n = len(color_map)

    print(f"Row span {row_span}")

    # Divide by n-2+black_mul to account for black bands around mid points
    repeat = int(row_span / (n - 2 + black_mul))
    print(f"Repeat LEDs: {repeat}x")

    # Repeat all colors without black bands
    col_seq = np.repeat(color_map[1:-1], repeat)

    # Add black bands based on multiple
    black_seq = np.repeat(color_map[0], int(repeat * black_mul / 2))

    print(col_seq.shape)

    col_seq = np.insert(col_seq, 0, black_seq)
    col_seq = np.append(col_seq, black_seq)
    print(col_seq.shape)

    # Add black padding for extra rows
    black_pad = row_span - col_seq.shape[0]
    half_black_pad = int(black_pad / 2)
    extra = 0 if (black_pad) % 2 == 0 else 1
    print(f"Black pad: {black_pad}")

    col_seq = np.insert(col_seq, color_map[0],
                        np.repeat(color_map[0], half_black_pad + extra))
    col_seq = np.append(col_seq,
                        np.repeat(color_map[0], half_black_pad))

    return col_seq


def calc_labels(nrows, mid_black, ton, toff, black_mul,
                resolution=10, fname='seq1.npy'):

    labels = np.ones(nrows*resolution) * LED_SEQUENCE.index("BLACK")

    # Generated color sequence of LEDs
    seq = np.load(fname)
    n_og_seq = calc_num_rows(ton, toff, seq.shape[1])
    color_map = make_color_map(seq, n_og_seq)

    black_rows = mid_black*resolution
    print(f"Black rows: {black_rows}")

    mid_row_span = np.diff(black_rows)
    print(f"Number of rows between black: {mid_row_span}")

    # Extend color map
    color_map_ext = extend_color_map(color_map, mid_row_span[0], black_mul)
    print(f"Color map size: {color_map_ext.shape}")

    # 0 <- first black row
    target_row_end = black_rows[0]
    while (True):
        target_row_start = max(target_row_end - color_map_ext.shape[0], 0)

        labels[target_row_start:target_row_end] = \
            np.array(color_map_ext[-(target_row_end-target_row_start):])

        target_row_end = target_row_start

        if (target_row_end <= 0):
            break

    # Inbetween all black rows
    for i in range(black_rows.shape[0]-1):
        color_map_ext = extend_color_map(color_map, mid_row_span[i], black_mul)
        labels[black_rows[i]:black_rows[i+1]] = np.array(color_map_ext)

    # last black row -> end
    target_row_start = black_rows[-1]
    while (True):
        target_row_end = target_row_start + color_map_ext.shape[0]

        labels[target_row_start:target_row_end] = \
            np.array(color_map_ext[:labels.shape[0]-target_row_start])

        target_row_start = target_row_end

        if (target_row_start > labels.shape[0]):
            break

    return labels, one_hot_encode(labels)


if __name__ == "__main__":

    seq = np.load('seq1.npy')
    # print(seq)
    # print(seq.shape)
    # print(calc_num_rows(15, 0, seq.shape[1]))

    np.set_printoptions(threshold=10000)
    np.random.seed(42)
    x = np.random.randint(0, 12, 100).reshape(10, 10)
    x[0, 0] = 0
    x[0, 1] = 11
    print(x)

    print(one_hot_encode(x))
