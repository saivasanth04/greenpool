import os

# The folders containing your Java files
folders = [
    "config",
    "controller",
    "dto",
    "entity",
    "repository",
    "service"
]

output_file = "sai.txt"

with open(output_file, "w", encoding="utf-8") as outfile:
    outfile.write("MY PROJECT JAVA FILES\n")
    outfile.write("=" * 50 + "\n\n")

    total_files = 0

    for folder in folders:
        folder_path = folder
        
        if not os.path.exists(folder_path):
            outfile.write(f"WARNING: Folder not found - {folder}\n\n")
            print(f"[MISSING] {folder}")
            continue
        
        outfile.write(f"{'=' * 20} {folder.upper()} {'=' * 20}\n\n")
        print(f"Processing {folder}...")

        files_found = False
        for root, dirs, files in os.walk(folder_path):
            for file in files:
                if file.endswith(".java"):
                    files_found = True
                    total_files += 1
                    file_path = os.path.join(root, file)
                    rel_path = os.path.relpath(file_path, ".")  # relative to current folder

                    outfile.write(f"----- FILE: {rel_path} -----\n\n")

                    try:
                        with open(file_path, "r", encoding="utf-8") as infile:
                            content = infile.read()
                        outfile.write(content)
                    except Exception as e:
                        outfile.write(f"ERROR reading {file_path}: {e}")

                    outfile.write("\n\n" + "-" * 80 + "\n\n")

        if not files_found:
            outfile.write("No .java files found in this folder.\n\n")

    outfile.write(f"\nTOTAL JAVA FILES COLLECTED: {total_files}\n")
    outfile.write("END OF FILE\n")

print(f"\nDONE! All Java files collected into {output_file}")
print(f"Total files: {total_files}")
