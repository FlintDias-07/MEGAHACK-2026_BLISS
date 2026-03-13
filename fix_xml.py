import os
import glob
import re

def escape_xml_strings(filepath):
    print(f"Processing {filepath}...")
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find text inside <string name="...">...</string>
    pattern = r'(<string name="[^"]+">)(.*?)(</string>)'
    
    def replacer(match):
        start_tag = match.group(1)
        text = match.group(2)
        end_tag = match.group(3)
        
        # Escape unescaped apostrophes
        # We look for ' that is not already escaped as \'
        text = re.sub(r"(?<!\\)'", r"\'", text)
        
        # Escape unescaped quotes if any
        # We look for " that is not already escaped as \"
        text = re.sub(r'(?<!\\)"', r'\"', text)
        
        return f"{start_tag}{text}{end_tag}"

    new_content = re.sub(pattern, replacer, content, flags=re.DOTALL)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)

if __name__ == '__main__':
    base_dir = r"app/src/main/res"
    # Find all strings.xml files in values-* directories
    for string_file in glob.glob(os.path.join(base_dir, 'values-*', 'strings.xml')):
        escape_xml_strings(string_file)
    print("Done escaping characters.")
