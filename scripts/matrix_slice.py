#!usr/bin/python3

import numpy as np

rows = 500

L = np.random.uniform(0, 1, [31, rows])
print(L.shape)

S = np.random.uniform(0, 1, [rows, 3000, 3000])
print(S.shape)

# gt_res = (S.T @ L.T)
# print(gt_res)
# print(gt_res.shape)

slices = 100
slice_size = int(rows/slices)

split_res = 0
for i in range(slices):
    print(i)

    start = i*slice_size
    end = i*slice_size + slice_size

    Li = L[:, start:end]
    print(Li.shape)

    Si = S[start:end, :, :]
    print(Si.shape)

    res = (Si.T @ Li.T)
    print(res.shape)

    split_res += res

# print(f"Original multiple res: {gt_res.shape}")
print(f"Split multiple res: {split_res.shape}")
# print(f"Equal: {np.all(np.isclose(gt_res, split_res))}")



