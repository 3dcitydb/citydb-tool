"""
Extract triangulated geometry from an IFC file using ifcopenshell.

Outputs a JSON file keyed by GlobalId. Each value is an object with:
  "polygons": list of flat coordinate arrays [x0,y0,z0, x1,y1,z1, ..., x0,y0,z0] (closed)
  "materials": list of per-face materials [r, g, b, transparency] or null

Usage:
    python extract_geometry.py <input.ifc> [output.geom.json]

If no output path is given, defaults to <input>.geom.json.
"""

import argparse
import ifcopenshell
import ifcopenshell.geom
import json
import math
import os
import sys

parser = argparse.ArgumentParser(description="Extract triangulated geometry from an IFC file")
parser.add_argument("input_ifc", help="Path to input IFC file")
parser.add_argument("output_json", nargs="?", default=None, help="Output geometry JSON path")
parser.add_argument("--reorient-shells", action="store_true",
                    help="Ensure outward-oriented solid boundaries")
args = parser.parse_args()

settings = ifcopenshell.geom.settings()
settings.set(settings.USE_WORLD_COORDS, True)
settings.set("triangulation-type", ifcopenshell.ifcopenshell_wrapper.TRIANGLE_MESH)
if args.reorient_shells:
    try:
        settings.set("reorient-shells", True)
    except Exception:
        pass

ifc_file = ifcopenshell.open(args.input_ifc)
result = {}
errors = 0

total = sum(1 for p in ifc_file.by_type("IfcProduct") if p.Representation is not None)
num_threads = os.cpu_count() or 1
print(f"Processing {total} products using {num_threads} threads")

iterator = ifcopenshell.geom.iterator(settings, ifc_file, num_threads)
count = 0

if iterator.initialize():
    while True:
        shape = iterator.get()
        count += 1
        guid = shape.guid

        try:
            element = ifc_file.by_guid(guid)
            print(f"\r[{count}/{total}] {element.is_a()} {element.Name or '':<40s}", end="", flush=True)

            verts = shape.geometry.verts
            faces = shape.geometry.faces

            # Extract per-face materials
            raw_materials = shape.geometry.materials
            material_ids = shape.geometry.material_ids

            # Build material lookup: index -> (r, g, b, transparency)
            mat_list = []
            for m in raw_materials:
                if hasattr(m, 'diffuse') and m.diffuse:
                    d = m.diffuse
                    r, g, b = d.r(), d.g(), d.b()
                else:
                    r, g, b = 0.8, 0.8, 0.8
                t = m.transparency if hasattr(m, 'transparency') and m.transparency is not None else 0.0
                if math.isnan(t) or math.isinf(t):
                    t = 0.0
                mat_list.append((r, g, b, t))

            polygons = []
            face_materials = []
            for i in range(0, len(faces), 3):
                tri = []
                for vi in [faces[i], faces[i + 1], faces[i + 2], faces[i]]:
                    tri.extend([verts[vi * 3], verts[vi * 3 + 1], verts[vi * 3 + 2]])
                polygons.append(tri)

                # Map face to material
                face_idx = i // 3
                if face_idx < len(material_ids) and material_ids[face_idx] >= 0 and material_ids[face_idx] < len(mat_list):
                    mat = mat_list[material_ids[face_idx]]
                    face_materials.append([round(mat[0], 6), round(mat[1], 6), round(mat[2], 6), round(mat[3], 6)])
                else:
                    face_materials.append(None)

            if polygons:
                result[guid] = {
                    "polygons": polygons,
                    "materials": face_materials
                }
        except Exception as e:
            errors += 1
            print(f"\nWarning: {guid}: {e}", file=sys.stderr)

        if not iterator.next():
            break

output_path = args.output_json if args.output_json else args.input_ifc.rsplit(".", 1)[0] + ".geom.json"
print()
with open(output_path, "w") as f:
    json.dump(result, f)
print(f"Extracted geometry for {len(result)} elements ({errors} errors) -> {output_path}")
