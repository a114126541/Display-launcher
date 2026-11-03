"""User-approved deterministic alpha matting of the clean, pale-background icon."""
from pathlib import Path
import json
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

HERE = Path(__file__).resolve().parent
im = Image.open(HERE / 'icon-desktop-opaque.png').convert('RGB')
rgb = np.asarray(im, dtype=np.float64)
h, w = rgb.shape[:2]
y, x = np.mgrid[0:h, 0:w]
xx, yy = x / (w - 1), y / (h - 1)
features = np.stack([np.ones_like(xx), xx, yy, xx*xx, xx*yy, yy*yy], axis=-1)
# The clean reference has an empty border. Fit its slight background shading, not the artwork.
border = (x < 24) | (x >= w - 24) | (y < 24) | (y >= h - 24)
coefficients = np.linalg.lstsq(features[border], rgb[border], rcond=None)[0]
background = features @ coefficients
distance = np.linalg.norm(rgb - background, axis=-1)
labels, count = ndi.label(distance > 12)
sizes = np.bincount(labels.ravel()); sizes[0] = 0
mask = labels == sizes.argmax()
mask = ndi.binary_fill_holes(ndi.binary_closing(mask, iterations=1))
core = ndi.binary_erosion(mask, iterations=2)
band = ndi.binary_dilation(mask, iterations=2)
_, indices = ndi.distance_transform_edt(~core, return_indices=True)
foreground = rgb[indices[0], indices[1]]
direction = foreground - background
alpha = np.clip(np.sum((rgb-background)*direction, axis=-1) / np.maximum(np.sum(direction*direction, axis=-1), 1), 0, 1)
alpha[core] = 1
alpha[~band] = 0
alpha[alpha < 0.04] = 0
edge_labels, edge_count = ndi.label(alpha > 0)
edge_sizes = np.bincount(edge_labels.ravel()); edge_sizes[0] = 0
alpha[edge_labels != edge_sizes.argmax()] = 0
# Undo the original background contribution at fractional-alpha edge pixels.
color = (rgb - background*(1-alpha[..., None])) / np.maximum(alpha[..., None], 0.001)
color[alpha == 0] = 0
rgba = np.dstack([np.clip(color, 0, 255).astype('uint8'), np.rint(alpha*255).astype('uint8')])
result = Image.fromarray(rgba)
result.save(HERE / 'logo-interface-transparent.png', optimize=True)
small = result.convert('RGBa').resize((256,256), Image.Resampling.LANCZOS).convert('RGBA')
small.save(HERE.parent / 'res/drawable-nodpi/header_logo.png', optimize=True)
for name, bg in [('light',(245,247,250,255)),('dark',(20,43,66,255))]:
    canvas = Image.new('RGBA', (320,320), bg)
    thumb = result.convert('RGBa').resize((288,288), Image.Resampling.LANCZOS).convert('RGBA')
    canvas.alpha_composite(thumb, (16,16))
    canvas.convert('RGB').save(HERE / ('logo-preview-'+name+'.png'))
labels, count = ndi.label(alpha > 0)
report = {'method':'border-fit background removal, largest component, foreground edge decontamination',
          'user_approved_local_processing':True,'alpha_components':int(count),
          'transparent_pixels':int(np.sum(alpha==0)), 'opaque_pixels':int(np.sum(alpha==1))}
(HERE/'logo-matting.json').write_text(json.dumps(report, indent=2)+'\n')
print(report)
