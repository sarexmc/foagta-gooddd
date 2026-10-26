#!/usr/bin/env python3
"""Reescala los sprites de luz del estudio con suavizado de calidad (Lanczos + premultiplicado + dither)
para que no se vean en bloques al dibujarlos grandes. Las originales estan en tools/originals/effect/."""
import numpy as np, os, json
from PIL import Image, ImageFilter
HERE=os.path.dirname(__file__)
SRC=os.path.join(HERE,'originals/effect/'); DST=os.path.join(HERE,'..','src/main/resources/assets/revivalcampfire/textures/effect/')
# nombre: (factor, es_degradado, repetir_en_u)
CFG={'brillo':(4,True,False),'centro':(4,True,False),'halo':(4,True,False),'haz':(8,True,False),'lobulos':(4,True,False),
     'rayos':(4,True,False),'estrella':(4,True,False),'fuego':(8,True,False),
     'aros':(3,False,False),'cinta':(4,False,True),'grieta':(4,False,False),'chispa':(8,False,False),'blanco':(1,False,False)}
rng=np.random.default_rng(1)
for name,(k,grad,rep) in CFG.items():
    im=Image.open(SRC+name+'.png').convert('RGBA'); w,h=im.size
    if k>1:
        a=np.asarray(im).astype(np.float32)/255.0
        pm=a.copy(); pm[...,:3]*=a[...,3:4]                       # premultiplicado: sin halos en los bordes
        chans=[Image.fromarray(pm[...,i]).resize((w*k,h*k),Image.LANCZOS) for i in range(4)]
        up=np.stack([np.asarray(c) for c in chans],-1)
        al=np.clip(up[...,3:4],1e-4,1); rgb=np.clip(up[...,:3]/al,0,1); alpha=np.clip(up[...,3:4],0,1)
        out=np.concatenate([rgb,alpha],-1)
        if grad:                                                   # degradados: suavizado + dither contra el banding
            o=Image.fromarray((out*255).astype(np.uint8),'RGBA').filter(ImageFilter.GaussianBlur(radius=k*0.35))
            out=np.asarray(o).astype(np.float32)/255.0
            noise=(rng.random(out.shape[:2]+(1,))-0.5)*(2.2/255.0)
            out[...,:3]=np.clip(out[...,:3]+noise,0,1)
        res=Image.fromarray((np.clip(out,0,1)*255+0.5).astype(np.uint8),'RGBA')
    else:
        res=im
    res.save(DST+name+'.png')
    meta={"texture":{"blur":True}}
    if not rep: meta["texture"]["clamp"]=True
    json.dump(meta,open(DST+name+'.png.mcmeta','w'))
    print(name,(w,h),'->',res.size)
