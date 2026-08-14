package com.w3n.wavestream.utils.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;

import com.hbb20.CCPCountry;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Component-list adapter used by the login country picker. */
public final class CountryAdapter extends ComponentList.Adapter<CCPCountry> {
    private static final float ROW_PADDING_DP = 16f;
    private static final float FLAG_WIDTH_DP = 38f;
    private static final float FLAG_HEIGHT_DP = 26f;
    private static final float NAME_TEXT_SP = 14f;
    private static final float CODE_TEXT_SP = 12f;
    private static final float DIVIDER_DESIGN_WIDTH = 670f;
    private static final float DIVIDER_DESIGN_WEIGHT = 2f;
    private static final int PRIMARY_TEXT_COLOR = 0xFF000E1A;
    private static final int MUTED_TEXT_COLOR = 0xFF7B8493;

    private final Context context;
    private final List<CountrySearchItem> searchIndex;
    private final List<CCPCountry> visibleCountries = new ArrayList<>();
    private final Paint codeMeasurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap placeholderFlag;
    private final Bitmap dividerBitmap;
    private final float designScale;
    private final FlagBitmapProvider flagBitmapProvider;
    private final MatchCountListener matchCountListener;

    public CountryAdapter(Context context, List<CountrySearchItem> searchIndex,
                          Bitmap placeholderFlag, Bitmap dividerBitmap, float designScale,
                          FlagBitmapProvider flagBitmapProvider,
                          MatchCountListener matchCountListener) {
        this.context = context;
        this.searchIndex = searchIndex;
        this.placeholderFlag = placeholderFlag;
        this.dividerBitmap = dividerBitmap;
        this.designScale = designScale;
        this.flagBitmapProvider = flagBitmapProvider;
        this.matchCountListener = matchCountListener;
        codeMeasurePaint.setTextSize(sp(CODE_TEXT_SP));
    }

    public void submitQuery(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        visibleCountries.clear();
        for (CountrySearchItem item : searchIndex) {
            if (normalizedQuery.isEmpty() || item.searchableText.contains(normalizedQuery)) {
                visibleCountries.add(item.country);
            }
        }
        notifyDataSetChanged();
        matchCountListener.onMatchCountChanged(visibleCountries.size());
    }

    @Override
    public CCPCountry getItem(int position) {
        return visibleCountries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return visibleCountries.get(position).getNameCode().hashCode();
    }

    @Override
    public int getItemCount() {
        return visibleCountries.size();
    }

    @Override
    public void onCreateItem(ComponentList.Item item, int viewType) {
        ComponentList.ItemScope scope = item.getScope();
        RectF bounds = scope.getBounds();
        float rowWidth = bounds.width();
        float rowHeight = bounds.height();
        float padding = dp(ROW_PADDING_DP);
        float flagWidth = dp(FLAG_WIDTH_DP);
        float flagHeight = dp(FLAG_HEIGHT_DP);
        float flagTop = (rowHeight - flagHeight) / 2f;
        float codeWidth = dp(92f);
        float dividerWidth = Math.min(rowWidth, DIVIDER_DESIGN_WIDTH * designScale);
        float dividerWeight = DIVIDER_DESIGN_WEIGHT * designScale;
        float dividerLeft = (rowWidth - dividerWidth) / 2f;

        ZLayer content = item.addLayer("content");
        content.add(new Image.Builder(context, scope.id("flag"), placeholderFlag,
                new RectF(padding, flagTop, padding + flagWidth, flagTop + flagHeight))
                .setScaleType(Image.ScaleType.FIT_XY));
        content.add(new Text.Builder(context, scope.id("name"), "",
                new RectF(padding + flagWidth + dp(ROW_PADDING_DP), 0f,
                        rowWidth - padding - codeWidth, rowHeight))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(NAME_TEXT_SP))
                .setTextColor(PRIMARY_TEXT_COLOR)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false));
        content.add(new Text.Builder(context, scope.id("code"), "",
                new RectF(rowWidth - padding - codeWidth, 0f, rowWidth - padding, rowHeight))
                .useDefaultFont()
                .setTextSizePx(sp(CODE_TEXT_SP))
                .setTextColor(MUTED_TEXT_COLOR)
                .setAlignment(Text.Alignment.END)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false));
        content.add(new Image.Builder(context, scope.id("divider"), dividerBitmap,
                new RectF(dividerLeft, rowHeight - dividerWeight,
                        dividerLeft + dividerWidth, rowHeight))
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    @Override
    public void onBindItem(ComponentList.Item item, CCPCountry country, int position) {
        RectF rowBounds = item.getScope().getBounds();
        float padding = dp(ROW_PADDING_DP);
        float nameLeft = padding + dp(FLAG_WIDTH_DP) + dp(ROW_PADDING_DP);
        String callingCode = "+" + country.getPhoneCode();
        float codeWidth = (float) Math.ceil(codeMeasurePaint.measureText(callingCode));
        float codeLeft = rowBounds.width() - padding - codeWidth;

        item.find("flag", Image.class).setBitmap(
                flagBitmapProvider.getFlagBitmap(country.getFlagID()));
        item.find("name", Text.class)
                .setRegion(new RectF(nameLeft, 0f, codeLeft, rowBounds.height()))
                .setText(country.getName());
        item.find("code", Text.class)
                .setRegion(new RectF(codeLeft, 0f,
                        rowBounds.width() - padding, rowBounds.height()))
                .setText(callingCode);
        item.find("divider", Image.class).setVisible(position < visibleCountries.size() - 1);
    }

    private float dp(float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * context.getResources().getDisplayMetrics().scaledDensity;
    }

    public interface FlagBitmapProvider {
        Bitmap getFlagBitmap(int drawableId);
    }

    public interface MatchCountListener {
        void onMatchCountChanged(int matchCount);
    }
}
