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
    "WHITE",
    "BLACK"
]


def calc_num_rows(ton, toff, size):

    denom = (ton + toff) / 5.0
    denom = denom if denom > 1 else 1

    return (int)(size/denom)


def consecutive(x, stepsize=3):
    return np.split(x, np.where(np.diff(x) > stepsize)[0]+1)


def one_hot_encode(x):
    x = np.array(x).astype(int)
    one_hot = np.zeros((x.size, x.max() + 1))
    one_hot[np.arange(x.size), x] = 1

    # Lazy zero blacks (black is index 12)
    one_hot = one_hot.T[1:].T

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


def calc_labels(nrows, black_rows, ton, toff, black_mul,
                resolution=10, fname='seq1.npy'):

    labels = np.ones(nrows*resolution) * LED_SEQUENCE.index("BLACK")

    # Generated color sequence of LEDs
    seq = np.load(fname)
    n_og_seq = calc_num_rows(ton, toff, seq.shape[1])
    color_map = make_color_map(seq, n_og_seq, v=True)
    n = len(color_map)

    black_rows = black_rows*resolution
    print(f"Black rows: {black_rows}")

    row_span = np.diff(black_rows)[0]
    print(f"Number of rows between black: {row_span}")
    repeat_leds = int(row_span / n)
    print(f"Repeat LEDs: {repeat_leds}x")

    color_map = np.repeat(color_map, repeat_leds)

    black_pad = int((row_span % repeat_leds)/2)

    # 0 -> first black row
    target_row = black_rows[0] - black_pad
    labels[0:target_row] = np.array(color_map[-target_row:])
    # print(labels[target_row-900:target_row+50])

    # Inbetween all black rows
    for i in range(black_rows.shape[0]-1):
        target_row_start = black_rows[i] + black_pad
        target_row_end = black_rows[i+1] - black_pad
        labels[target_row_start:target_row_end] = np.array(color_map)

    # last black row -> end
    target_row = black_rows[-1] + black_pad
    labels[target_row:] = np.array(color_map[:len(labels)-target_row])
    # print(labels[target_row:target_row+900])

    return one_hot_encode(labels)


if __name__ == "__main__":

    seq = np.load('seq1.npy')
    # print(seq)
    # print(seq.shape)
    # print(calc_num_rows(15, 0, seq.shape[1]))

    print(one_hot_encode(np.array([12.0, 2, 10, 1, 6, 3, 7, 4, 3, 1, 0])))
