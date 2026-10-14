package com.example.webtts;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Dong ket qua tim sub: thumbnail trai + ten ban release phai.
 * Khong poster (SubDL) thi an anh, chu chiem full chieu rong.
 */
public class SubResultAdapter extends ArrayAdapter<SubResult> {

    private static class Holder {
        ImageView iv;
        TextView tv;
    }

    public SubResultAdapter(Context ctx, List<SubResult> rs) {
        super(ctx, 0, rs);
    }

    @Override
    public View getView(int pos, View cv, ViewGroup parent) {
        Context ctx = getContext();
        SubResult r = getItem(pos);
        Holder h;
        if (cv == null) {
            float den = ctx.getResources().getDisplayMetrics().density;
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding((int) (12 * den), (int) (8 * den),
                    (int) (12 * den), (int) (8 * den));
            ImageView iv = new ImageView(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (54 * den), (int) (81 * den));
            lp.setMarginEnd((int) (10 * den));
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFF1C2128);
            row.addView(iv);
            TextView tv = new TextView(ctx);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(14);
            row.addView(tv);
            h = new Holder();
            h.iv = iv;
            h.tv = tv;
            row.setTag(h);
            cv = row;
        } else {
            h = (Holder) cv.getTag();
        }
        if (r != null) {
            h.tv.setText(r.label());
            SubThumbLoader.load(h.iv, r.posterUrl);
        }
        return cv;
    }
}
