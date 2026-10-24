package ru.kstolu.app;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.view.View;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.ViewOutlineProvider;
import android.widget.Button;
/** Explicit, flat rounded geometry: OEM button shadows/tints must not square its corners. */
final class RoundedButton {
 private RoundedButton(){}
 static void apply(Button button,int color,int radiusDp){
  float density=button.getResources().getDisplayMetrics().density;
  GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(radiusDp*density);shape.setStroke(Math.max(1,Math.round(density)),0xffdfe6d8);
  GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(radiusDp*density);
  button.setBackgroundTintList(null);button.setStateListAnimator(null);button.setElevation(0);button.setTranslationZ(0);
  button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x204c6441),shape,mask));
  button.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),radiusDp*density);}});button.setClipToOutline(true);button.setIncludeFontPadding(false);
  button.setMinimumHeight(Math.round(48*density));button.setMinHeight(Math.round(48*density));
  button.setMinimumWidth(0);button.setMinWidth(0);
 }
}
