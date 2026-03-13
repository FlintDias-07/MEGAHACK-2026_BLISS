import os
import xml.etree.ElementTree as ET
from deep_translator import GoogleTranslator
import time

def translate_incremental(base_file, target_file, lang_code, lang_name):
    print(f"Translating missing keys to {lang_name} ({lang_code})...")
    
    # Read base keys
    tree_base = ET.parse(base_file)
    root_base = tree_base.getroot()
    base_dict = {}
    for child in root_base:
        if child.tag == 'string' and child.text and not child.text.startswith('@'):
            base_dict[child.attrib['name']] = child.text
            
    # Read existing target keys
    target_dict = {}
    if os.path.exists(target_file):
        try:
            tree_target = ET.parse(target_file)
            root_target = tree_target.getroot()
            for child in root_target:
                if child.tag == 'string':
                    target_dict[child.attrib['name']] = child.text
        except ET.ParseError:
            print(f"Warning: {target_file} is malformed. Recreating.")
            root_target = ET.Element('resources')
            tree_target = ET.ElementTree(root_target)
    else:
        root_target = ET.Element('resources')
        tree_target = ET.ElementTree(root_target)
        
    translator = GoogleTranslator(source='en', target=lang_name)
    count = 0
    
    for key, text in base_dict.items():
        if key not in target_dict:
            try:
                translated_text = translator.translate(text)
                if translated_text:
                    # Fix common formatting corruptions for placeholders like %1$d
                    translated_text = translated_text.replace('% 1 $ d', '%1$d').replace('%1 $ d', '%1$d').replace('% 1$d', '%1$d')
                    translated_text = translated_text.replace('% 1 $ s', '%1$s').replace('%1 $ s', '%1$s').replace('% 1$s', '%1$s')
                    
                    elem = ET.Element('string', name=key)
                    elem.text = translated_text
                    elem.tail = '\n    '
                    root_target.append(elem)
                    count += 1
                    time.sleep(0.1) # Small delay to avoid rate limiting
            except Exception as e:
                print(f"Error translating '{text}': {e}")
                pass
                
    if count > 0:
        os.makedirs(os.path.dirname(target_file), exist_ok=True)
        tree_target.write(target_file, encoding='utf-8', xml_declaration=True)
        print(f"Added {count} translations to {lang_name}.")
    else:
        print(f"{lang_name} is up to date.")

languages = {
    'bn': 'bengali',
    'ta': 'tamil',
    'te': 'telugu',
    'kn': 'kannada',
    'gu': 'gujarati',
    'pa': 'punjabi',
    'ml': 'malayalam',
    'or': 'odia (oriya)',
    'hi': 'hindi',
    'mr': 'marathi'
}

base_path = r'app/src/main/res/values/strings.xml'

for code, name in languages.items():
    out_dir = f'app/src/main/res/values-{code}'
    out_file = f'{out_dir}/strings.xml'
    translate_incremental(base_path, out_file, code, name)
print("Incremental translations completed.")
