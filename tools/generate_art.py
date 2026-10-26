#!/usr/bin/env python3
"""Genera la textura de la cuchara y la del menu."""
import random, json, os
from PIL import Image, ImageDraw
A=os.path.join(os.path.dirname(__file__),'..','src/main/resources/assets/revivalcampfire/')
rnd=random.Random(21)
cl=lambda v:max(0,min(255,int(v)))
def tint(c,d): return tuple(cl(x+d) for x in c)

# ---- cuchara 16x16 (contorno automatico) ----
O=(46,26,12)
wood=[(224,160,92),(190,128,66),(152,100,52),(112,70,36)]
gold=[(255,236,130),(236,184,58),(172,116,30)]
leaf=[(130,226,96),(56,168,64),(30,110,50)]
gem=[(130,250,255),(40,170,200)]
fill={}
for x in range(16):
    for y in range(16):
        if ((x-11)/3.5)**2+((y-4)/2.9)**2<=1.0:
            s=(x-11)+(y-4); fill[(x,y)]=wood[0 if s<-2 else 1 if s<0 else 2 if s<2 else 3]
for p in [(10,3),(11,3),(9,4)]: fill[p]=wood[0]
for i in range(8):
    x,y=8-i,6+i; fill[(x,y)]=gold[1]; fill[(x+1,y)]=gold[2]
    if i%2==0: fill[(x,y-1)]=gold[0]
fill[(2,12)]=gem[0]; fill[(1,13)]=gem[1]; fill[(2,13)]=gem[1]
for (x,y,c) in [(5,4,2),(6,4,0),(4,5,2),(5,5,1),(6,5,0),(7,5,1),(5,6,2),(6,6,1),(7,6,1),(6,7,2),(3,6,2),(4,7,1),(4,6,1)]:
    fill[(x,y)]=leaf[c]
S=Image.new('RGBA',(16,16),(0,0,0,0))
for (x,y),c in fill.items(): S.putpixel((x,y),(*c,255))
for (x,y) in list(fill):
    for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
        nx,ny=x+dx,y+dy
        if 0<=nx<16 and 0<=ny<16 and (nx,ny) not in fill and S.getpixel((nx,ny))[3]==0:
            S.putpixel((nx,ny),(*O,255))
S.save(A+'textures/item/resurrection_spoon.png')

# ---- menu: marco 176x154 ----
W,H=176,154
tex=Image.new('RGBA',(256,256),(0,0,0,0))
def wood_tex(w,h,base,vertical,seed):
    r=random.Random(seed); im=Image.new('RGBA',(w,h)); n=(w if vertical else h)
    sh=[r.randint(-9,9) for _ in range(n)]
    for x in range(w):
        for y in range(h):
            k=sh[x] if vertical else sh[y]
            im.putpixel((x,y),(*tint(base,k+r.randint(-4,4)),255))
    d=ImageDraw.Draw(im)
    for _ in range(max(1,w*h//900)):
        if vertical:
            x=r.randint(0,w-1); y=r.randint(0,max(0,h-6)); d.line([(x,y),(x,y+r.randint(3,9))],fill=(*tint(base,-26),255))
        else:
            y=r.randint(0,h-1); x=r.randint(0,max(0,w-6)); d.line([(x,y),(x+r.randint(3,9),y)],fill=(*tint(base,-26),255))
    return im
def paste(im,x,y): tex.paste(im,(x,y))
paste(wood_tex(164,142,(48,30,19),True,1),6,6)
paste(wood_tex(164,13,(84,57,38),False,2),6,6)
paste(wood_tex(164,12,(66,43,28),False,3),6,132)
d=ImageDraw.Draw(tex)
for yy in (6,18,132,143): d.line([(6,yy),(169,yy)],fill=(34,21,13,255))
d.line([(6,7),(169,7)],fill=(112,80,52,255)); d.line([(6,133),(169,133)],fill=(96,66,44,255))
def panel(w,h,seed):
    r=random.Random(seed); im=Image.new('RGBA',(w,h))
    for i in range(w):
        for j in range(h):
            vig=min(1,min(i,w-1-i)/6,min(j,h-1-j)/6)
            im.putpixel((i,j),(*tint((124,92,48),int(r.randint(-5,5)-14*(1-vig))),255))
    dd=ImageDraw.Draw(im)
    for k in range(1,10):
        yy=12*k
        if yy<h-2: dd.line([(2,yy),(w-3,yy)],fill=(104,75,38,255))
    dd.rectangle([0,0,w-1,h-1],outline=(70,46,22,255))
    dd.line([(1,1),(w-2,1)],fill=(146,110,56,255)); dd.line([(1,1),(1,h-2)],fill=(146,110,56,255))
    return im
paste(panel(65,111,5),20,20); paste(panel(65,111,6),93,20)
paste(wood_tex(8,111,(72,44,27),True,7),85,20)
d.line([(85,20),(85,130)],fill=(40,24,15,255)); d.line([(92,20),(92,130)],fill=(40,24,15,255))
for px,sd in ((6,8),(157,9)):
    paste(wood_tex(13,148,(88,60,41),True,sd),px,0)
    d.rectangle([px,0,px+12,147],outline=(36,22,14,255))
    d.line([(px+2,1),(px+2,146)],fill=(124,92,64,255)); d.line([(px+10,1),(px+10,146)],fill=(56,36,24,255))
    d.rectangle([px,0,px+12,3],fill=(60,40,26,255),outline=(36,22,14,255))
    for cy in (8,60,112):
        d.rectangle([px+4,cy,px+8,cy+4],fill=(192,138,58,255),outline=(110,74,28,255)); d.point((px+5,cy+1),fill=(255,226,140,255))
    d.rectangle([px+2,140,px+10,147],fill=(192,138,58,255),outline=(110,74,28,255))
    d.line([(px+3,141),(px+9,141)],fill=(255,226,140,255)); d.line([(px+3,146),(px+9,146)],fill=(150,100,36,255))
d.rectangle([113,52,136,75],fill=(62,41,25,255),outline=(30,19,12,255))
d.rectangle([115,54,134,73],fill=(30,20,13,255),outline=(20,13,8,255))
d.line([(114,53),(135,53)],fill=(100,70,44,255)); d.line([(114,53),(114,74)],fill=(100,70,44,255))
tex.save(A+'textures/gui/revive_menu.png')

# vistas previas
pv=Image.new('RGBA',(W*3,H*3),(70,110,60,255)); pv.alpha_composite(tex.crop((0,0,W,H)).resize((W*3,H*3),Image.NEAREST)); pv.save('/home/claude/menu_preview.png')
sp=Image.new('RGBA',(256,256),(70,90,120,255)); sp.alpha_composite(S.resize((256,256),Image.NEAREST)); sp.save('/home/claude/spoon_preview.png')
print('ok')
