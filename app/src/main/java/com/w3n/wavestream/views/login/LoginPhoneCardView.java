package com.w3n.wavestream.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hbb20.CountryCodePicker;
import com.w3n.wavestream.R;
import com.w3n.wavestream.views.animator.WaveAnimatorView;
import com.w3n.wavestream.views.animator.button.ButtonViewAnimator;
import com.w3n.wavestream.views.animator.dialog.CustomViewDialog;
import com.w3n.wavestream.views.animator.dialog.MessageBubbleDialog;

public class LoginPhoneCardView extends FrameLayout {
    private LinearLayout contentLayout;
    private WaveAnimatorView animatorView;
    private View getOtpButton;
    private Button confirmButton;
    private AnimatorClickListener otpClickListener;
    private AnimatorClickListener confirmClickListener;

    public LoginPhoneCardView(Context context) {
        super(context);
        init();
    }

    public LoginPhoneCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LoginPhoneCardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundResource(R.drawable.bg_pinggo_card);
        setElevation(0.007639f * getRefWidth());
        setClipChildren(false);
        setClipToPadding(false);

        contentLayout = new LinearLayout(getContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.setPadding((int) (0.063657f * getRefWidth()), (int) (0.071296f * getRefWidth()),
                (int) (0.063657f * getRefWidth()), (int) (0.063657f * getRefWidth()));
        addView(contentLayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        buildContent();

        animatorView = new WaveAnimatorView(getContext());
        addView(animatorView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        post(this::rebuildAnimators);
    }

    private void buildContent() {
        TextView hiddenFlag = new TextView(getContext());
        hiddenFlag.setId(R.id.countryFlagTextView);
        hiddenFlag.setVisibility(GONE);
        contentLayout.addView(hiddenFlag, new LinearLayout.LayoutParams(1, 1));

        TextView title = text(R.string.enter_phone_number, 20, R.color.primary_text, true);
        contentLayout.addView(title, matchWrap());

        TextView hint = text(R.string.verification_code_hint, 13, R.color.pinggo_body_text, false);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(0.007639f * getRefWidth(), 1f);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = (int) (0.030556f * getRefWidth());
        contentLayout.addView(hint, hintParams);

        contentLayout.addView(countryPickerFrame(), topMargin((int) (0.045833f * getRefWidth()), (int) (0.162963f * getRefWidth())));
        contentLayout.addView(phoneInputFrame(), topMargin((int) (0.030556f * getRefWidth()), (int) (0.168056f * getRefWidth())));
        contentLayout.addView(securityMessage(), topMargin((int) (0.045833f * getRefWidth()), LinearLayout.LayoutParams.WRAP_CONTENT));
        getOtpButton = otpButton();
        contentLayout.addView(getOtpButton, topMargin((int) (0.063657f * getRefWidth()), (int) (0.127315f * getRefWidth())));

        TextView otpHint = text(R.string.otp_hint, 14, R.color.secondary_text, false);
        otpHint.setId(R.id.otpHintTextView);
        otpHint.setVisibility(GONE);
        LinearLayout.LayoutParams otpHintParams = matchWrap();
        otpHintParams.topMargin = (int) (0.045833f * getRefWidth());
        contentLayout.addView(otpHint, otpHintParams);

        EditText otpEditText = new EditText(getContext());
        otpEditText.setId(R.id.otpEditText);
        otpEditText.setHint(R.string.enter_otp);
        otpEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        otpEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        otpEditText.setSingleLine(true);
        otpEditText.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.043287f * getRefWidth());
        otpEditText.setPadding((int) (0.040741f * getRefWidth()), 0, (int) (0.040741f * getRefWidth()), 0);
        otpEditText.setBackgroundResource(R.drawable.bg_pinggo_input);
        otpEditText.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_text));
        otpEditText.setHintTextColor(ContextCompat.getColor(getContext(), R.color.secondary_text));
        otpEditText.setVisibility(GONE);
        LinearLayout.LayoutParams otpParams = topMargin((int) (0.020370f * getRefWidth()), (int) (0.142593f * getRefWidth()));
        contentLayout.addView(otpEditText, otpParams);

        confirmButton = new Button(getContext());
        confirmButton.setId(R.id.confirmButton);
        confirmButton.setText(R.string.confirm);
        confirmButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.045833f * getRefWidth());
        confirmButton.setVisibility(GONE);
        contentLayout.addView(confirmButton, topMargin((int) (0.040741f * getRefWidth()), (int) (0.142593f * getRefWidth())));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int contentWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        contentLayout.measure(contentWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int contentHeight = contentLayout.getMeasuredHeight();
        int exactHeightSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY);
        animatorView.measure(contentWidthSpec, exactHeightSpec);
        setMeasuredDimension(width, contentHeight);
    }

    private FrameLayout countryPickerFrame() {
        FrameLayout frame = new FrameLayout(getContext());
        View background = new View(getContext());
        background.setBackgroundResource(R.drawable.bg_pinggo_input);
        frame.addView(background, frameParams(Gravity.BOTTOM, LayoutParams.MATCH_PARENT, (int) (0.142593f * getRefWidth())));

        CountryCodePicker picker = new CountryCodePicker(getContext());
        picker.setId(R.id.countryCodePicker);
        picker.setBackgroundResource(R.drawable.bg_pinggo_input);
        picker.setPadding((int) (0.030556f * getRefWidth()), 0, (int) (0.112037f * getRefWidth()), 0);
        picker.setGravity(Gravity.CENTER_VERTICAL);
        picker.setDefaultCountryUsingNameCode("IN");
        picker.resetToDefaultCountry();
        picker.showArrow(false);
        picker.showFlag(true);
        picker.showFullName(true);
        picker.showNameCode(false);
        picker.setShowPhoneCode(false);
        picker.setTextSize((int) (0.040741f * getRefWidth()));
        frame.addView(picker, frameParams(Gravity.BOTTOM, LayoutParams.MATCH_PARENT, (int) (0.142593f * getRefWidth())));

        ImageView arrow = new ImageView(getContext());
        arrow.setImageResource(R.drawable.ic_dropdown_down);
        arrow.setColorFilter(ContextCompat.getColor(getContext(), R.color.primary_text));
        FrameLayout.LayoutParams arrowParams = frameParams(Gravity.END | Gravity.BOTTOM,
                (int) (0.061111f * getRefWidth()), (int) (0.061111f * getRefWidth()));
        arrowParams.setMargins(0, 0, (int) (0.045833f * getRefWidth()), (int) (0.040741f * getRefWidth()));
        frame.addView(arrow, arrowParams);

        View.OnClickListener openCountryPicker = v -> picker.launchCountrySelectionDialog();
        frame.setClickable(true);
        background.setClickable(true);
        arrow.setClickable(true);
        frame.setOnClickListener(openCountryPicker);
        background.setOnClickListener(openCountryPicker);
        arrow.setOnClickListener(openCountryPicker);

        TextView label = smallLabel(R.string.country, R.color.pinggo_muted_text);
        FrameLayout.LayoutParams labelParams = frameParams(Gravity.START | Gravity.TOP, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = (int) (0.035648f * getRefWidth());
        frame.addView(label, labelParams);
        return frame;
    }

    private FrameLayout phoneInputFrame() {
        FrameLayout frame = new FrameLayout(getContext());
        LinearLayout row = new LinearLayout(getContext());
        row.setId(R.id.phoneInputContainer);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (0.045833f * getRefWidth()), 0, (int) (0.035648f * getRefWidth()), 0);
        row.setBackgroundResource(R.drawable.bg_pinggo_phone_input);
        frame.addView(row, frameParams(Gravity.BOTTOM, LayoutParams.MATCH_PARENT, (int) (0.147685f * getRefWidth())));

        TextView code = new TextView(getContext());
        code.setId(R.id.phoneCodeTextView);
        code.setGravity(Gravity.CENTER);
        code.setText("+91");
        code.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.045833f * getRefWidth());
        code.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_text));
        row.addView(code, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        View divider = new View(getContext());
        divider.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.pinggo_input_stroke));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                (int) (0.002546f * getRefWidth()), (int) (0.063657f * getRefWidth()));
        dividerParams.leftMargin = (int) (0.045833f * getRefWidth());
        row.addView(divider, dividerParams);

        EditText phone = new EditText(getContext());
        phone.setId(R.id.phoneNumberEditText);
        phone.setHint(R.string.phone_number);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setSingleLine(true);
        phone.setBackgroundColor(0x00000000);
        phone.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.043287f * getRefWidth());
        phone.setPadding((int) (0.045833f * getRefWidth()), 0, 0, 0);
        phone.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_text));
        phone.setHintTextColor(ContextCompat.getColor(getContext(), R.color.secondary_text));
        row.addView(phone, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        TextView label = smallLabel(R.string.phone_number, R.color.pinggo_action);
        FrameLayout.LayoutParams labelParams = frameParams(Gravity.START | Gravity.TOP, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = (int) (0.035648f * getRefWidth());
        frame.addView(label, labelParams);
        return frame;
    }

    private LinearLayout securityMessage() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        ImageView lock = new ImageView(getContext());
        lock.setImageResource(R.drawable.ic_pinggo_lock);
        row.addView(lock, new LinearLayout.LayoutParams((int) (0.035648f * getRefWidth()), (int) (0.035648f * getRefWidth())));
        TextView message = text(R.string.phone_safe_message, 13, R.color.pinggo_body_text, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = (int) (0.025463f * getRefWidth());
        row.addView(message, params);
        return row;
    }

    private View otpButton() {
        FrameLayout button = new FrameLayout(getContext());
        button.setId(R.id.getOtpButton);
        button.setBackgroundResource(R.drawable.bg_pinggo_button);
        button.setClickable(true);
        button.setFocusable(true);

        TextView label = text(R.string.next, 18, R.color.white, true);
        button.addView(label, frameParams(Gravity.CENTER, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        ImageView arrow = new ImageView(getContext());
        arrow.setImageResource(R.drawable.ic_pinggo_arrow);
        arrow.setColorFilter(ContextCompat.getColor(getContext(), R.color.white));
        FrameLayout.LayoutParams arrowParams = frameParams(Gravity.END | Gravity.CENTER_VERTICAL,
                (int) (0.050926f * getRefWidth()), (int) (0.050926f * getRefWidth()));
        arrowParams.rightMargin = (int) (0.071296f * getRefWidth());
        button.addView(arrow, arrowParams);
        return button;
    }

    public void setOnOtpAnimatorClickListener(AnimatorClickListener listener) {
        otpClickListener = listener;
        getOtpButton.setOnClickListener(v -> performOtpClick());
        rebuildAnimators();
    }

    public void setOnConfirmAnimatorClickListener(AnimatorClickListener listener) {
        confirmClickListener = listener;
        confirmButton.setOnClickListener(v -> performConfirmClick());
        rebuildAnimators();
    }

    public void refreshAnimators() {
        post(this::rebuildAnimators);
    }

    public void showAnimatorDialog(String message) {
        CustomViewDialog.addDialog(animatorView.getDialogs(), new MessageBubbleDialog(message),
                animatorView, true, "login_card_message", id -> animatorView.invalidate());
    }

    private void rebuildAnimators() {
        animatorView.getButtonAnimators().clear();
        animatorView.getTextAnimators().clear();
        addButtonAnimator("get_otp", getOtpButton, id -> performOtpClick());
        if (confirmButton.getVisibility() == VISIBLE) {
            addButtonAnimator("confirm_otp", confirmButton, id -> performConfirmClick());
        }
        animatorView.invalidate();
    }

    private void addButtonAnimator(String id, View sourceView, ButtonViewAnimator.OnClickListener listener) {
        if (sourceView.getWidth() == 0 || sourceView.getHeight() == 0) {
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(sourceView.getWidth(), sourceView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        sourceView.draw(canvas);
        sourceView.setAlpha(0f);
        animatorView.getButtonAnimators().add(new ButtonViewAnimator(listener, id, bitmap, rectInCard(sourceView)));
    }

    private void performOtpClick() {
        pulseView(getOtpButton, "otp_pulse");
        if (otpClickListener != null) {
            otpClickListener.onClick();
        }
    }

    private void performConfirmClick() {
        pulseView(confirmButton, "confirm_pulse");
        if (confirmClickListener != null) {
            confirmClickListener.onClick();
        }
    }

    private void pulseView(View sourceView, String id) {
        Bitmap bitmap = Bitmap.createBitmap(sourceView.getWidth(), sourceView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float previousAlpha = sourceView.getAlpha();
        sourceView.setAlpha(1f);
        sourceView.draw(canvas);
        sourceView.setAlpha(previousAlpha);
        animatorView.pulseBitmap(id, bitmap, rectInCard(sourceView));
    }

    private RectF rectInCard(View sourceView) {
        int[] source = new int[2];
        int[] overlay = new int[2];
        sourceView.getLocationOnScreen(source);
        animatorView.getLocationOnScreen(overlay);
        float left = source[0] - overlay[0];
        float top = source[1] - overlay[1];
        return new RectF(left, top, left + sourceView.getWidth(), top + sourceView.getHeight());
    }

    private TextView text(int stringId, int textSize, int colorId, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(stringId);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeInPixels(textSize));
        view.setTextColor(ContextCompat.getColor(getContext(), colorId));
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private TextView smallLabel(int stringId, int colorId) {
        TextView label = text(stringId, 12, colorId, false);
        label.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.white));
        label.setPadding((int) (0.010185f * getRefWidth()), 0, (int) (0.010185f * getRefWidth()), 0);
        return label;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int topMargin, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height);
        params.topMargin = topMargin;
        return params;
    }

    private FrameLayout.LayoutParams frameParams(int gravity, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = gravity;
        return params;
    }

    private int getRefWidth() {
        return getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
    }

    private float textSizeInPixels(int textSize) {
        switch (textSize) {
            case 20:
                return 0.050926f * getRefWidth();
            case 18:
                return 0.045833f * getRefWidth();
            case 14:
                return 0.035648f * getRefWidth();
            case 13:
                return 0.033102f * getRefWidth();
            case 12:
                return 0.030556f * getRefWidth();
            default:
                return 0.033102f * getRefWidth();
        }
    }

    public interface AnimatorClickListener {
        void onClick();
    }
}
