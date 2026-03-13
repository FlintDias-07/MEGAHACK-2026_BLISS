import os
import glob
import xml.etree.ElementTree as ET

def fix_android_strings(filepath):
    print(f"Fixing {filepath}...")
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
        changed = False

        for string_tag in root.findall('string'):
            text = string_tag.text
            if text:
                # Remove any existing manual Python escaping we did before
                text = text.replace("\\'", "'").replace('\\"', '"')
                
                # Apply Android-safe string escaping
                if text.startswith('"') and text.endswith('"'):
                    # Already wrapped in quotes
                    pass
                elif "'" in text:
                    # Has apostrophe -> wrap the whole thing in double quotes
                    # And escape any existing double quotes inside
                    text = '"' + text.replace('"', '\\"') + '"'
                    changed = True
                elif '"' in text:
                    # Has double quote but no apostrophe -> just escape it
                    text = text.replace('"', '\\"')
                    changed = True
                
                string_tag.text = text
        
        # Write back if changed
        tree.write(filepath, encoding='utf-8', xml_declaration=True)
    except Exception as e:
        print(f"Error processing {filepath}: {e}")

if __name__ == '__main__':
    base_dir = r"app/src/main/res"
    # Find all strings.xml files in values-* directories
    for string_file in glob.glob(os.path.join(base_dir, 'values-*', 'strings.xml')):
        fix_android_strings(string_file)
    print("All string files fixed via double-quoting.")
