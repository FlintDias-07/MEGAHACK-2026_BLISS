import os
import re
import glob

strings_xml_path = r"app/src/main/res/values/strings.xml"
kotlin_files = glob.glob(r"app/src/main/java/**/*.kt", recursive=True)

with open(strings_xml_path, 'r', encoding='utf-8') as f:
    strings_content = f.read()

# Get existing keys and values
existing_keys = set(re.findall(r'<string name="([^"]+)"', strings_content))
existing_values = {}
for match in re.finditer(r'<string name="([^"]+)">([^<]+)</string>', strings_content):
    existing_values[match.group(2)] = match.group(1)

new_strings = {} # Text -> Key
added_files = set()

def generate_key(text):
    # remove emojis and special chars, lowercase, replace space with _
    clean = re.sub(r'[^a-zA-Z0-9\s]', '', text).strip().lower()
    key = re.sub(r'\s+', '_', clean)
    if not key:
        return "text_" + str(hash(text))[:6]
    return "extracted_" + key

for kt_file in kotlin_files:
    with open(kt_file, 'r', encoding='utf-8') as f:
        content = f.read()
    original_content = content
    
    # We want to match: Text("Something...") or Text(text = "Something...")
    # Be careful not to replace things with string interpolation '$' or escapes '\'
    pattern = re.compile(r'(Text\(\s*(?:text\s*=\s*)?)"([^"$\\]+)"')
    
    def replacer(match):
        prefix = match.group(1)
        text_val = match.group(2)
        
        # Don't extract very short strings like empty or 1 char unless it's a specific word
        if len(text_val.strip()) < 2:
            return match.group(0)
            
        if text_val in existing_values:
            key = existing_values[text_val]
        elif text_val in new_strings:
            key = new_strings[text_val]
        else:
            key = generate_key(text_val)
            orig_key = key
            counter = 1
            while key in existing_keys or key in new_strings.values():
                key = f"{orig_key}_{counter}"
                counter += 1
            new_strings[text_val] = key
            
        return f'{prefix}stringResource(R.string.{key})'

    new_content, count = pattern.subn(replacer, content)
    
    if count > 0 and new_content != original_content:
        # Add imports if missing
        import_string_res = 'import androidx.compose.ui.res.stringResource\n'
        import_r = 'import com.safepulse.R\n'
        
        has_string_res = 'import androidx.compose.ui.res.stringResource' in new_content
        has_r = 'import com.safepulse.R' in new_content
        
        if not has_string_res or not has_r:
            imports_to_add = ("" if has_string_res else import_string_res) + ("" if has_r else import_r)
            
            pkg_match = re.search(r'^package\s+.*$', new_content, re.MULTILINE)
            if pkg_match:
                insert_pos = pkg_match.end()
                new_content = new_content[:insert_pos] + '\n\n' + imports_to_add.strip() + '\n' + new_content[insert_pos:]
                
        with open(kt_file, 'w', encoding='utf-8') as f:
            f.write(new_content)
        added_files.add(kt_file)

if new_strings:
    print(f"Adding {len(new_strings)} strings to strings.xml...")
    # insert before </resources>
    insert_str = "\n    <!-- Extracted UI Strings -->\n"
    for text_val, key in new_strings.items():
        insert_str += f'    <string name="{key}">{text_val}</string>\n'
    
    strings_content = strings_content.replace('</resources>', insert_str + '</resources>')
    
    with open(strings_xml_path, 'w', encoding='utf-8') as f:
        f.write(strings_content)
        
    for text_val, key in new_strings.items():
        print(f"Extracted: {text_val} -> {key}")
else:
    print("No strings extracted.")
print(f"Modified {len(added_files)} Kotlin files.")
