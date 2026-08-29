import sys

file_path = "app/src/gms/kotlin/com/music/echo/ui/component/CastDevicePickerSheet.kt"
with open(file_path, "r") as f:
    lines = f.readlines()

out_lines = []
for i, line in enumerate(lines):
    if line.strip() == "companion object {" and "companion object {" in [l.strip() for l in out_lines[-10:]]:
        continue
    out_lines.append(line)

with open(file_path, "w") as f:
    f.writelines(out_lines)
