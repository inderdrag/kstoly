package ru.kstolu.app;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Simple native vector icons, no icon font or network resources. */
final class UiIcon extends Drawable {
    private final String kind;private final int color,size;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    UiIcon(String kind,int color,int size){this.kind=kind;this.color=color;this.size=size;}
    private void line(Canvas c,float...xy){Path p=new Path();p.moveTo(xy[0],xy[1]);for(int j=2;j<xy.length;j+=2)p.lineTo(xy[j],xy[j+1]);c.drawPath(p,paint);}
    @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);paint.setColor(color);paint.setStrokeWidth(1.7f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        switch(kind){
            case "home":line(c,3,10,12,3,21,10,21,21,3,21,3,10);line(c,9,21,9,13,15,13,15,21);break;
            case "book":line(c,5,3,19,3,19,21,12,17,5,21,5,3);break;
            case "settings":c.drawCircle(12,12,4,paint);for(int j=0;j<8;j++){double a=j*Math.PI/4;line(c,12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10);}break;
            case "camera":c.drawRoundRect(3,6,21,21,3,3,paint);line(c,8,6,10,3,14,3,16,6);c.drawCircle(12,13,4,paint);break;
            case "spark":line(c,12,2,15,9,22,12,15,15,12,22,9,15,2,12,9,9,12,2);break;
            case "back":line(c,14,5,7,12,14,19);break;
            case "receipt":line(c,5,3,19,3,19,21,15,19,12,21,8,19,5,21,5,3);line(c,9,8,15,8);line(c,9,12,15,12);break;
            case "share":c.drawCircle(6,12,3,paint);c.drawCircle(18,5,3,paint);c.drawCircle(18,19,3,paint);line(c,9,10,15,6);line(c,9,14,15,18);break;
            default:line(c,9,5,16,12,9,19);
        }c.restore();
    }
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
