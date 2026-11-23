"""GPU regression for menu water/aurora seams. Requires moderngl and numpy.
Run with the same GPU/driver used by Minecraft; no game connection is made.
"""
from pathlib import Path
import moderngl
import numpy as np
source=(Path(__file__).resolve().parents[1]/'src/main/resources/assets/floydaddons/shaders/core/menu_landscape.fsh').read_text()
ctx=moderngl.create_standalone_context(require=330);print(ctx.info['GL_RENDERER'],flush=True)
vertex='#version 330\nin vec2 p; void main(){gl_Position=vec4(p,0.,1.);}'
vbo=ctx.buffer(np.array([-1,-1,1,-1,-1,1,1,1],dtype='f4').tobytes())
def render(frag,width,height,uniforms=None):
 prog=ctx.program(vertex_shader=vertex,fragment_shader=frag)
 for name,value in (uniforms or {}).items():
  if name in prog:prog[name].value=value
 vao=ctx.vertex_array(prog,[(vbo,'2f','p')]);fb=ctx.simple_framebuffer((width,height),components=4,dtype='f4');fb.use();ctx.viewport=(0,0,width,height);vao.render(moderngl.TRIANGLE_STRIP)
 a=np.frombuffer(fb.read(components=4,dtype='f4'),dtype='f4').reshape(height,width,4).copy();vao.release();prog.release();fb.release();return a
noise=source[source.index('float Hash(float n)'):source.index('float Fbm(')]
probe='#version 330\nuniform int axis;\nout vec4 outColor;\n'+noise+'''void main(){
 int x=int(gl_FragCoord.x);float boundary=float(x/2)-64.;
 vec2 p=vec2(boundary+(x%2==0?-.00001:.00001),(gl_FragCoord.y-64.)*.137+.31);
 if(axis==1)p=p.yx;
 float n=Noise(p);outColor=vec4(n,n,n,1.);}
'''
for axis in (0, 1):
    samples = render(probe, 256, 128, {"axis": axis})
    jumps = np.abs(samples[:, ::2, 0] - samples[:, 1::2, 0])
    worst = float(jumps.max())
    print(f"Axis {axis}: largest cell-edge jump {worst:.9f}", flush=True)
    assert worst < .001, f"Procedural noise is discontinuous across axis {axis} lattice cells"
vbo.release()
ctx.release()
print("Menu noise continuity passed on the active OpenGL GPU.")
