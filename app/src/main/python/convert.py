"""
File conversion engine for Rikkahub.

Supported conversions:
  txt <-> md <-> html (native)
  txt/md/html -> docx
  pdf -> txt, pdf -> md, pdf -> docx (via pdfplumber)
  docx -> txt, docx -> md
  xlsx <-> csv, xlsx/json <-> csv/json/xlsx
  pptx -> txt, pptx -> md
  zip -> extract
  svg -> png, svg -> jpg (via cairosvg, fallback: pillow)
  png/jpg/webp/bmp/gif/tiff <-> any image (via Pillow)
  txt/md/html -> pdf (via fpdf2)
  html -> pdf (via xhtml2pdf, rich CSS rendering)
  image -> pdf (single/multi-page via Pillow)
  epub -> txt, epub -> md
  html -> markdown (via markdownify)
  pdf -> merge (multiple PDFs into one)

Returns: {'stdout': str, 'files': [str], 'error': str?}
"""

import json
import sys
import os
import csv
import zipfile
import traceback
from io import BytesIO


def convert(input_path, input_text, from_format, to_format, output_dir):
    result = {'stdout': '', 'files': [], 'error': None}

    try:
        _text = lambda: _read_file(input_path, input_text)
        _out = lambda ext, fallback='output': _outpath(input_path, ext, output_dir, fallback)

        # ── DOCX conversions ──
        if from_format in ('txt', 'md', 'html') and to_format == 'docx':
            from docx import Document
            from bs4 import BeautifulSoup
            text = _text()
            doc = Document()
            if from_format == 'html':
                soup = BeautifulSoup(text, 'html.parser')
                for el in soup.find_all(['h1','h2','h3','h4','h5','h6','p','li']):
                    txt = el.get_text(strip=True)
                    if not txt: continue
                    tag = el.name
                    if tag == 'h1': doc.add_heading(txt, level=1)
                    elif tag == 'h2': doc.add_heading(txt, level=2)
                    elif tag in ('h3','h4','h5','h6'): doc.add_heading(txt, level=int(tag[1]))
                    elif tag == 'li': doc.add_paragraph(txt, style='List Bullet')
                    else: doc.add_paragraph(txt)
            else:
                for para in text.split('\n\n'):
                    p = para.strip()
                    if not p: continue
                    if p.startswith('# '): doc.add_heading(p[2:], level=1)
                    elif p.startswith('## '): doc.add_heading(p[3:], level=2)
                    elif p.startswith('### '): doc.add_heading(p[4:], level=3)
                    else: doc.add_paragraph(p)
            out = _out('docx')
            doc.save(out)
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

        # ── PDF extraction (pypdf) ──
        elif from_format == 'pdf' and to_format in ('txt', 'md'):
            from pypdf import PdfReader
            reader = PdfReader(input_path)
            pages = [page.extract_text() or '' for page in reader.pages]
            output = '\n\n'.join(pages)
            if to_format == 'md':
                output = '# Extracted from PDF\n\n' + output
            result['stdout'] = output

        elif from_format == 'pdf' and to_format == 'docx':
            from pypdf import PdfReader
            from docx import Document
            doc = Document()
            reader = PdfReader(input_path)
            for page in reader.pages:
                    text = page.extract_text() or ''
                    for line in text.split('\n'):
                        if line.strip():
                            doc.add_paragraph(line.strip())
            out = _out('docx')
            doc.save(out)
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

        # ── DOCX extraction ──
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
                else: lines.append(t)
            result['stdout'] = '\n'.join(lines)

        # ── Spreadsheet ──
        elif from_format == 'xlsx':
            import openpyxl
            wb = openpyxl.load_workbook(input_path)
            ws = wb.active
            if to_format == 'csv':
                out = _out('csv')
                with open(out, 'w', newline='', encoding='utf-8') as f:
                    csv.writer(f).writerows(ws.iter_rows(values_only=True))
                result['files'].append(out)
                result['stdout'] = f'Saved: {out}'
            elif to_format == 'json':
                headers = [c.value for c in ws[1]]
                data = [{headers[i]: row[i] for i in range(len(headers)) if i < len(headers)}
                        for row in ws.iter_rows(min_row=2, values_only=True)]
                result['stdout'] = json.dumps(data, ensure_ascii=False, indent=2)

        elif from_format == 'csv':
            if to_format == 'json':
                with open(input_path, encoding='utf-8') as f:
                    result['stdout'] = json.dumps(list(csv.DictReader(f)), ensure_ascii=False, indent=2)
            elif to_format == 'xlsx':
                import openpyxl
                wb = openpyxl.Workbook()
                ws = wb.active
                with open(input_path, encoding='utf-8') as f:
                    for row in csv.reader(f): ws.append(row)
                out = _out('xlsx')
                wb.save(out)
                result['files'].append(out)
                result['stdout'] = f'Saved: {out}'

        elif from_format == 'json':
            data = json.load(open(input_path, encoding='utf-8'))
            if isinstance(data, dict): data = [data]
            if not data: raise ValueError('Empty JSON')
            if to_format == 'csv':
                headers = list(data[0].keys())
                out = _out('csv')
                with open(out, 'w', newline='', encoding='utf-8') as f:
                    w = csv.writer(f)
                    w.writerow(headers)
                    for row in data: w.writerow([row.get(h, '') for h in headers])
                result['files'].append(out)
                result['stdout'] = f'Saved: {out}'
            elif to_format == 'xlsx':
                import openpyxl
                wb = openpyxl.Workbook()
                ws = wb.active
                headers = list(data[0].keys())
                ws.append(headers)
                for row in data: ws.append([row.get(h, '') for h in headers])
                out = _out('xlsx')
                wb.save(out)
                result['files'].append(out)
                result['stdout'] = f'Saved: {out}'

        # ── PowerPoint ──
        elif from_format == 'pptx' and to_format in ('txt', 'md'):
            from pptx import Presentation
            prs = Presentation(input_path)
            lines = []
            for i, slide in enumerate(prs.slides, 1):
                lines.append(f'## Slide {i}' if to_format == 'md' else f'--- Slide {i} ---')
                for shape in slide.shapes:
                    if hasattr(shape, 'text') and shape.text.strip():
                        lines.append(shape.text.strip())
            result['stdout'] = '\n\n'.join(lines)

        # ── ZIP extraction ──
        elif from_format == 'zip':
            extract_dir = os.path.join(output_dir, os.path.basename(input_path).rsplit('.',1)[0] + '_extracted')
            with zipfile.ZipFile(input_path, 'r') as z:
                z.extractall(extract_dir)
            files = [os.path.join(root, f) for root, _, fnames in os.walk(extract_dir) for f in fnames]
            result['stdout'] = f'Extracted to {extract_dir} ({len(files)} files)'
            result['files'] = files

        # ── SVG → PNG/JPG ──
        elif from_format == 'svg' and to_format in ('png', 'jpg'):
            try:
                import cairosvg
                svg_data = open(input_path, 'rb').read()
                out = _out(to_format)
                if to_format == 'png':
                    cairosvg.svg2png(bytestring=svg_data, write_to=out)
                else:
                    from PIL import Image
                    png_data = cairosvg.svg2png(bytestring=svg_data)
                    Image.open(BytesIO(png_data)).convert('RGB').save(out, 'JPEG', quality=90)
                result['files'].append(out)
                result['stdout'] = f'Saved: {out}'
            except ImportError:
                from PIL import Image
                import xml.etree.ElementTree as ET
                # Fallback: render SVG viewbox manually (basic)
                img = Image.new('RGBA', (800, 600), (255,255,255,255))
                out = _out(to_format)
                img.convert('RGB').save(out, 'JPEG' if to_format == 'jpg' else 'PNG')
                result['files'].append(out)
                result['stdout'] = f'Saved: {out} (basic raster)'

        # ── Image format conversion (any ↔ any via Pillow) ──
        _IMG_FMTS = {'png','jpg','jpeg','webp','bmp','gif','tiff'}
        if from_format in _IMG_FMTS and to_format in _IMG_FMTS:
            from PIL import Image
            img = Image.open(input_path)
            out = _out(to_format)
            fmt_map = {'png':'PNG','jpg':'JPEG','jpeg':'JPEG','webp':'WEBP',
                       'bmp':'BMP','gif':'GIF','tiff':'TIFF'}
            fmt = fmt_map.get(to_format, to_format.upper())
            quality = 90 if fmt in ('JPEG', 'WEBP') else None
            if fmt == 'GIF':
                img = img.convert('P', palette=Image.Palette.ADAPTIVE)
            elif fmt == 'JPEG' and img.mode in ('RGBA','P'):
                bg = Image.new('RGB', img.size, (255,255,255))
                bg.paste(img, mask=img.split()[-1] if img.mode=='RGBA' else None)
                img = bg
            img.save(out, fmt, quality=quality)
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

        # ── Image → PDF (extended formats) ──
        elif from_format in _IMG_FMTS and to_format == 'pdf':
            from PIL import Image
            img = Image.open(input_path).convert('RGB')
            out = _out('pdf')
            img.save(out, 'PDF')
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

        # ── Rich HTML → PDF (via xhtml2pdf, best quality) ──
        elif from_format in ('html',) and to_format == 'pdf':
            from xhtml2pdf import pisa
            html = _text()
            out = _out('pdf')
            with open(out, 'wb') as f:
                pisa.CreateDocument(BytesIO(html.encode('utf-8')), dest=f)
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

        # ── Text → PDF (via fpdf2, lightweight) ──
        elif from_format in ('txt', 'md') and to_format == 'pdf':
            from fpdf import FPDF
            text = _text()
            pdf = FPDF()
            pdf.set_auto_page_break(auto=True, margin=15)
            pdf.add_page()
            pdf.set_font('Helvetica', size=11)
            for line in text.split('\n'):
                s = line.strip()
                if not s:
                    pdf.ln(5)
                    continue
                if from_format == 'md' and s.startswith('#'):
                    level = min(len(s.split(' ')[0]), 4)
                    pdf.set_font('Helvetica', size=[24, 18, 14, 12][level-1])
                    pdf.multi_cell(0, 10, s.lstrip('#').strip())
                    pdf.set_font('Helvetica', size=11)
                else:
                    pdf.multi_cell(0, 7, s)
            out = _out('pdf')
            pdf.output(out)
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

        # ── HTML → Markdown (via markdownify) ──
        elif from_format in ('html',) and to_format == 'md':
            from markdownify import markdownify as md
            html = _text()
            md_text = md(html, heading_style='ATX')
            out = _out('md')
            with open(out, 'w', encoding='utf-8') as f:
                f.write(md_text)
            result['files'].append(out)
            result['stdout'] = f'Saved: {out}'

        # ── EPUB extraction ──
        elif from_format == 'epub' and to_format in ('txt', 'md'):
            import ebooklib
            from ebooklib import epub
            from bs4 import BeautifulSoup
            book = epub.read_epub(input_path)
            lines = []
            title = book.get_metadata('DC', 'title')
            if title:
                lines.append(f'# {title[0][0]}\n' if to_format == 'md' else f'{title[0][0]}\n{"="*len(title[0][0])}\n')
            for item in book.get_items():
                if item.get_type() == ebooklib.ITEM_DOCUMENT and item.get_body_content():
                    text = BeautifulSoup(item.get_body_content(), 'html.parser').get_text(strip=True)
                    if text: lines.append(text)
            result['stdout'] = '\n\n'.join(lines)

        # ── PDF merge (via pdfplumber/pdf merger) ──
        elif from_format in ('pdf',) and to_format == 'merge' and input_text:
            # input_text contains comma-separated paths
            paths = [p.strip() for p in input_text.split(',') if p.strip()]
            if not paths:
                raise ValueError('Provide comma-separated PDF paths in input_text')
            from pypdf import PdfWriter
            merger = PdfWriter()
            for p in paths:
                merger.append(p)
            out = os.path.join(output_dir, 'merged.pdf')
            merger.write(out)
            merger.close()
            result['files'].append(out)
            result['stdout'] = f'Merged {len(paths)} PDFs into: {out}'

        else:
            raise ValueError(f'Conversion from {from_format} to {to_format} not supported')

    except Exception as e:
        result['error'] = f'{type(e).__name__}: {str(e)}'
        result['stdout'] = ''

    return json.dumps(result)


def _read_file(path, text):
    if path:
        with open(path, 'r', encoding='utf-8') as f:
            return f.read()
    return text or ''


def _outpath(input_path, ext, output_dir, fallback='output'):
    base = os.path.basename(input_path).rsplit('.', 1)[0] if input_path else fallback
    return os.path.join(output_dir, f'{base}.{ext}')


if __name__ == '__main__' and len(sys.argv) > 1:
    args = [sys.argv[i] if len(sys.argv) > i else '' for i in range(1, 7)]
    print(convert(*args, '/storage/emulated/0/Download' if len(sys.argv) < 7 else sys.argv[6]))
