import json, sys, os, csv, re, base64

def convert(input_path, input_text, from_format, to_format, output_dir):
    """File conversion entry point. Returns {'stdout': str, 'files': [str]}"""
    result = {'stdout': '', 'files': []}

    if from_format in ('txt',) and to_format in ('docx', 'html', 'md'):
        text = open(input_path, 'r', encoding='utf-8').read() if input_path else input_text

    if from_format == 'txt' and to_format == 'docx':
        from docx import Document
        doc = Document()
        for para in text.split('\n\n'):
            p = para.strip()
            if not p: continue
            if p.startswith('# '): doc.add_heading(p[2:], level=1)
            elif p.startswith('## '): doc.add_heading(p[3:], level=2)
            elif p.startswith('### '): doc.add_heading(p[4:], level=3)
            else: doc.add_paragraph(p)
        out = os.path.join(output_dir, os.path.basename(input_path or 'output').rsplit('.',1)[0] + '.docx')
        doc.save(out)
        result['files'].append(out)
        result['stdout'] = f'Saved: {out}'

    elif from_format == 'md' and to_format == 'docx':
        from docx import Document
        doc = Document()
        text = open(input_path, 'r', encoding='utf-8').read() if input_path else input_text
        for line in text.split('\n'):
            s = line.strip()
            if s.startswith('# '): doc.add_heading(s[2:], level=1)
            elif s.startswith('## '): doc.add_heading(s[3:], level=2)
            elif s.startswith('### '): doc.add_heading(s[4:], level=3)
            elif s.startswith('- ') or s.startswith('* '): doc.add_paragraph(s, style='List Bullet')
            else: doc.add_paragraph(s)
        out = os.path.join(output_dir, os.path.basename(input_path or 'output').rsplit('.',1)[0] + '.docx')
        doc.save(out)
        result['files'].append(out)
        result['stdout'] = f'Saved: {out}'

    elif from_format == 'pdf' and to_format in ('txt', 'md'):
        from pypdf import PdfReader
        reader = PdfReader(input_path)
        text = []
        for page in reader.pages:
            t = page.extract_text()
            if t: text.append(t)
        output = '\n\n'.join(text)
        if to_format == 'md':
            output = '# Extracted from PDF\n\n' + output
        result['stdout'] = output

    elif from_format == 'docx' and to_format in ('txt', 'md'):
        from docx import Document
        doc = Document(input_path)
        lines = []
        for p in doc.paragraphs:
            t = p.text.strip()
            if not t: continue
            if to_format == 'md':
                style = p.style.name.lower() if p.style else ''
                if 'heading 1' in style: lines.append(f'# {t}')
                elif 'heading 2' in style: lines.append(f'## {t}')
                elif 'heading 3' in style: lines.append(f'### {t}')
                elif 'list' in style: lines.append(f'- {t}')
                else: lines.append(t)
            else:
                lines.append(t)
        result['stdout'] = '\n'.join(lines)

    elif from_format == 'xlsx':
        import openpyxl
        wb = openpyxl.load_workbook(input_path)
        if to_format == 'csv':
            ws = wb.active
            out = os.path.join(output_dir, os.path.basename(input_path).rsplit('.',1)[0] + '.csv')
            with open(out, 'w', newline='', encoding='utf-8') as f:
                w = csv.writer(f)
                for row in ws.iter_rows(values_only=True):
                    w.writerow(row)
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'
        elif to_format == 'json':
            ws = wb.active
            headers = [c.value for c in ws[1]]
            data = []
            for row in ws.iter_rows(min_row=2, values_only=True):
                data.append({headers[i]: row[i] for i in range(len(headers)) if i < len(headers)})
            result['stdout'] = json.dumps(data, ensure_ascii=False)

    elif from_format == 'csv':
        if to_format == 'json':
            with open(input_path, 'r', encoding='utf-8') as f:
                reader = csv.DictReader(f)
                data = list(reader)
            result['stdout'] = json.dumps(data, ensure_ascii=False)
        elif to_format == 'xlsx':
            import openpyxl
            wb = openpyxl.Workbook()
            ws = wb.active
            with open(input_path, 'r', encoding='utf-8') as f:
                reader = csv.reader(f)
                for row in reader:
                    ws.append(row)
            out = os.path.join(output_dir, os.path.basename(input_path).rsplit('.',1)[0] + '.xlsx')
            wb.save(out)
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

    elif from_format == 'json':
        if to_format == 'csv':
            data = json.load(open(input_path, 'r', encoding='utf-8'))
            if isinstance(data, dict): data = [data]
            if not data: raise ValueError('Empty JSON data')
            headers = list(data[0].keys())
            out = os.path.join(output_dir, os.path.basename(input_path).rsplit('.',1)[0] + '.csv')
            with open(out, 'w', newline='', encoding='utf-8') as f:
                w = csv.writer(f)
                w.writerow(headers)
                for row in data:
                    w.writerow([row.get(h, '') for h in headers])
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

    elif from_format == 'pptx' and to_format in ('txt', 'md'):
        from pptx import Presentation
        prs = Presentation(input_path)
        lines = []
        for i, slide in enumerate(prs.slides, 1):
            if to_format == 'md':
                lines.append(f'## Slide {i}')
            else:
                lines.append(f'--- Slide {i} ---')
            for shape in slide.shapes:
                if hasattr(shape, 'text') and shape.text.strip():
                    lines.append(shape.text.strip())
        result['stdout'] = '\n\n'.join(lines)

    elif from_format == 'zip':
        import zipfile
        extract_dir = os.path.join(output_dir, os.path.basename(input_path).rsplit('.',1)[0] + '_extracted')
        with zipfile.ZipFile(input_path, 'r') as z:
            z.extractall(extract_dir)
        files = []
        for root, dirs, fnames in os.walk(extract_dir):
            for fname in fnames:
                files.append(os.path.join(root, fname))
        result['stdout'] = f'Extracted to {extract_dir} ({len(files)} files)'
        result['files'] = files

    else:
        raise ValueError(f'Conversion from {from_format} to {to_format} not supported')

    return json.dumps(result)


if __name__ == '__main__':
    input_path = sys.argv[1] if len(sys.argv) > 1 else ''
    input_text = sys.argv[2] if len(sys.argv) > 2 else ''
    from_format = sys.argv[3] if len(sys.argv) > 3 else 'txt'
    to_format = sys.argv[4] if len(sys.argv) > 4 else 'txt'
    output_dir = sys.argv[5] if len(sys.argv) > 5 else '/storage/emulated/0/Download'
    print(convert(input_path, input_text, from_format, to_format, output_dir))
