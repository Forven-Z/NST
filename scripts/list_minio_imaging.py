from collections import defaultdict

from minio import Minio

c = Minio("127.0.0.1:9001", access_key="minioadmin", secret_key="minioadmin123", secure=False)
by_study: dict[str, list[str]] = defaultdict(list)
for o in c.list_objects("imaging", prefix="studies/", recursive=True):
    parts = o.object_name.split("/")
    if len(parts) >= 2:
        by_study[parts[1]].append(o.object_name)

for sid in sorted(by_study, key=lambda x: (not x.isdigit(), int(x) if x.isdigit() else 999999, x)):
    objs = by_study[sid]
    sources = [p for p in objs if "/source/" in p]
    masks = [p for p in objs if p.endswith("mask.nii.gz")]
    previews = [p for p in objs if "ct_preview" in p]
    print(f"studies/{sid}/  total={len(objs)}  source={len(sources)}  mask={len(masks)}  preview={len(previews)}")
    for p in previews + masks:
        print(f"  {p}")
    if sources:
        print(f"  source sample: {sources[0]}")
    print()
