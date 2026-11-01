package com.techpp.glass;

import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TodayPanel extends LinearLayout {
    static final int ACTION_NEWS_SECTION = 3;
    static final int ACTION_HEADLINE_BASE = 4;

    private final float density;
    private final LinearLayout calendar, newsCard, headlines, market, quotes;
    private final TextView calendarTitle;
    private final TextView dayNumber, weekday, date, festival;
    private final TextView tithi, nakshatra, varjyam, kalam, durmuhurtham;
    private final TextView newsStatus, quoteStatus;
    private final TextView colorToggle, darkToggle, addStock, newsTitle;
    private final List<LinearLayout> headlineRows = new ArrayList<>();
    private final List<Today.Headline> displayedHeadlines = new ArrayList<>();
    private final List<TextView> paletteTexts = new ArrayList<>();
    private java.util.function.Consumer<Today.Headline> headlineClickListener;
    private Runnable newsSectionClickListener;
    private int textColor, mutedColor;
    private boolean calendarColor, calendarDark;
    private int focusedAction = -1;
    private boolean dayTheme = true;
    private boolean staticViewsReady;

    TodayPanel(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        android.content.SharedPreferences prefs = context.getSharedPreferences("today", Context.MODE_PRIVATE);
        calendarColor = prefs.getBoolean("calendar_color", true);
        calendarDark = prefs.getBoolean("calendar_dark", false);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(10), dp(10), dp(10), dp(10));
        setClipChildren(false);
        setClipToPadding(false);

        calendar = card(context);
        calendar.setPadding(dp(10), dp(8), dp(10), dp(7));
        LinearLayout.LayoutParams calendarParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.05f);
        calendarParams.setMarginEnd(dp(8));
        addView(calendar, calendarParams);

        market = card(context);
        market.setOrientation(VERTICAL);
        market.setPadding(dp(10), dp(10), dp(10), dp(8));
        LinearLayout.LayoutParams marketParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.05f);
        marketParams.setMarginEnd(dp(8));
        addView(market, marketParams);

        newsCard = card(context);
        newsCard.setOrientation(VERTICAL);
        newsCard.setPadding(dp(12), dp(10), dp(12), dp(8));
        addView(newsCard, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 2f));

        LinearLayout calendarHeading = new LinearLayout(context);
        calendarHeading.setGravity(Gravity.CENTER_VERTICAL);
        calendarHeading.setOrientation(HORIZONTAL);
        calendar.addView(calendarHeading, new LayoutParams(LayoutParams.MATCH_PARENT, dp(22)));
        calendarTitle = text("TELUGU PANCHANGAM", 9, true);
        calendarTitle.setLetterSpacing(0.02f);
        calendarTitle.setSingleLine(true);
        calendarTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        calendarHeading.addView(calendarTitle, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout calendarToggles = new LinearLayout(context);
        calendarToggles.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        calendarToggles.setOrientation(HORIZONTAL);
        colorToggle = toggle(context, "Color");
        darkToggle = toggle(context, "Dark");
        calendarToggles.addView(colorToggle, toggleParams());
        calendarToggles.addView(darkToggle, toggleParams());
        calendarHeading.addView(calendarToggles, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(20)));

        LinearLayout hero = new LinearLayout(context);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50));
        calendar.addView(hero, heroLp);

        dayNumber = text("", 36, true);
        dayNumber.setIncludeFontPadding(false);
        hero.addView(dayNumber, new LayoutParams(dp(52), LayoutParams.WRAP_CONTENT));
        LinearLayout dayLabels = new LinearLayout(context);
        dayLabels.setOrientation(VERTICAL);
        hero.addView(dayLabels, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        weekday = text("", 13, true);
        date = text("", 8, false);
        date.setMaxLines(2);
        dayLabels.addView(weekday, wrap());
        dayLabels.addView(date, wrap());

        festival = text("", 9, true);
        festival.setSingleLine(false);
        festival.setMaxLines(2);
        festival.setGravity(Gravity.CENTER_VERTICAL);
        festival.setPadding(dp(5), 0, dp(5), 0);
        LinearLayout.LayoutParams festivalLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(30));
        calendar.addView(festival, festivalLp);

        tithi = detail("తిథి");
        nakshatra = detail("నక్షత్రం");
        varjyam = detail("వర్జ్యం");
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(20));
        calendar.addView(tithi, rowLp);
        calendar.addView(nakshatra, rowLp);
        calendar.addView(varjyam, rowLp);

        LinearLayout timings = new LinearLayout(context);
        timings.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams timingsLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        timingsLp.topMargin = dp(3);
        calendar.addView(timings, timingsLp);
        kalam = timing(context, "యమకంటక కాలం");
        durmuhurtham = timing(context, "దుర్ముహూర్తం");
        timings.addView(kalam, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        timings.addView(durmuhurtham, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        TextView marketTitle = text("INDIA MARKETS", 11, true);
        marketTitle.setLetterSpacing(0.04f);
        market.addView(marketTitle, wrap());
        quoteStatus = text("", 9, false);
        market.addView(quoteStatus, wrap());

        quotes = new LinearLayout(context);
        quotes.setOrientation(VERTICAL);
        market.addView(quotes, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addStock = text("+  Manage stocks  ·  0/" + Today.MAX_MANUAL_STOCKS, 11, true);
        addStock.setFocusable(false);
        addStock.setGravity(Gravity.CENTER);
        addStock.setPadding(dp(8), 0, dp(8), 0);
        addStock.setSingleLine(true);
        addStock.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addStock.setBackground(rounded(0x332D6CDF, dp(8)));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(23));
        addLp.topMargin = dp(3);
        market.addView(addStock, addLp);

        newsTitle = text("INDIA & WORLD  ·  HEADLINES", 11, true);
        newsTitle.setLetterSpacing(0.04f);
        newsTitle.setFocusable(true);
        newsTitle.setClickable(true);
        newsCard.addView(newsTitle, wrap());
        newsCard.setClickable(true);
        newsStatus = text("", 9, false);
        newsStatus.setSingleLine(true);
        newsCard.addView(newsStatus, wrap());
        headlines = new LinearLayout(context);
        headlines.setOrientation(VERTICAL);
        LinearLayout.LayoutParams headlineLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f);
        headlineLp.topMargin = dp(2);
        newsCard.addView(headlines, headlineLp);

        staticViewsReady = true;
    }

    public TodayPanel(Context context, AttributeSet attrs) { this(context); }

    View calendarColorButton() { return colorToggle; }
    View calendarDarkButton() { return darkToggle; }
    View addStockButton() { return addStock; }
    View headlineButton(int index) {
        return index >= 0 && index < headlineRows.size() ? headlineRows.get(index) : null;
    }
    View newsSectionButton() { return newsTitle; }
    Today.Headline headlineAt(int index) {
        return index >= 0 && index < displayedHeadlines.size() ? displayedHeadlines.get(index) : null;
    }
    List<Today.Headline> displayedHeadlines() { return new ArrayList<>(displayedHeadlines); }
    int headlineCount() { return headlineRows.size(); }
    void setHeadlineClickListener(java.util.function.Consumer<Today.Headline> listener) {
        headlineClickListener = listener;
    }
    void setNewsSectionClickListener(Runnable listener) {
        newsSectionClickListener = listener;
        View.OnClickListener openNews = view -> {
            if (newsSectionClickListener != null) newsSectionClickListener.run();
        };
        newsTitle.setOnClickListener(openNews);
        newsCard.setOnClickListener(openNews);
    }

    void setSnapshot(Today.Snapshot snapshot, boolean dayTheme, int text, int muted) {
        this.dayTheme = dayTheme;
        textColor = text;
        mutedColor = muted;
        newsCard.setBackground(rounded(dayTheme ? 0xFFF9FAFC : 0xFF24262D, dp(12)));
        market.setBackground(rounded(dayTheme ? 0xFFF9FAFC : 0xFF24262D, dp(12)));
        updateActionStyles();
        for (TextView view : paletteTexts) view.setTextColor(mutedColor);
        applyCalendarStyle(0xFF3549A2);

        Today.CalendarDay current = Today.calendarToday();
        dayNumber.setText(String.valueOf(current.day));
        weekday.setText(current.weekday);
        date.setText(current.date.toUpperCase(Locale.ROOT) + "\n" + current.month);
        festival.setText(current.festival == null ? "" : current.festival);
        festival.setVisibility(current.festival == null ? GONE : VISIBLE);
        festival.setTextColor(Color.WHITE);
        festival.setBackground(rounded(calendarColor ? 0xFF3549A2 : 0xFF626779, dp(5)));
        dayNumber.setTextColor(calendarColor ? 0xFF3549A2 : calendarTextColor());
        weekday.setTextColor(calendarTextColor());
        date.setTextColor(calendarMutedColor());
        tithi.setTextColor(calendarTextColor());
        nakshatra.setTextColor(calendarTextColor());
        varjyam.setTextColor(calendarTextColor());
        kalam.setTextColor(calendarMutedColor());
        durmuhurtham.setTextColor(calendarMutedColor());
        if (current.hasAlmanac()) {
            tithi.setText("తిథి  ·  " + current.tithi);
            nakshatra.setText("నక్షత్రం  ·  " + current.nakshatra);
            varjyam.setText("వర్జ్యం  ·  " + current.varjyam);
            kalam.setText(current.kalamName + "\n" + current.kalam);
            durmuhurtham.setText("దుర్ముహూర్తం\n" + current.durmuhurtham);
        } else {
            tithi.setText("ఈ తేదీకి పంచాంగ వివరాలు అందుబాటులో లేవు");
            nakshatra.setText("");
            varjyam.setText("");
            kalam.setText("");
            durmuhurtham.setText("");
        }

        headlines.removeAllViews();
        headlineRows.clear();
        displayedHeadlines.clear();
        int shown = Math.min(8, snapshot.headlines.size());
        for (int i = 0; i < shown; i++) {
            Today.Headline item = snapshot.headlines.get(i);
            displayedHeadlines.add(item);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(7), 0, dp(7), 0);
            row.setFocusable(true);
            row.setClickable(true);
            styleHeadlineRow(row, i);
            row.setOnClickListener(view -> {
                if (headlineClickListener != null) headlineClickListener.accept(item);
            });
            TextView section = text(item.section, 8, true);
            section.setTextColor(item.section.startsWith("WORLD") ? 0xFF6D88C7 : 0xFF328A78);
            row.addView(section, new LayoutParams(dp(92), LayoutParams.WRAP_CONTENT));
            TextView headline = text(item.title, 10, false);
            headline.setSingleLine(true);
            headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(headline, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
            lp.topMargin = dp(2);
            headlines.addView(row, lp);
            headlineRows.add(row);
        }
        long now = System.currentTimeMillis();
        if (shown == 0) newsStatus.setText(snapshot.headlinesRetryAt > now ? "NEWS UNAVAILABLE · RETRYING" : "Connecting to news feeds…");
        else newsStatus.setText(snapshot.headlinesRetryAt > now ? "STALE · INDIA & WORLD · BUSINESS · FINANCE · TECH · POLITICS"
                : "INDIA & WORLD · BUSINESS · FINANCE · TECH · POLITICS");

        quotes.removeAllViews();
        addQuoteRow(snapshot.quotes, "^BSESN", "Sensex");
        addQuoteRow(snapshot.quotes, "^NSEI", "Nifty 50");
        for (String symbol : snapshot.manualSymbols) addQuoteRow(snapshot.quotes, symbol, stockName(symbol));
        addStock.setText("+  Manage stocks  ·  " + snapshot.manualSymbols.size() + "/"
                + Today.MAX_MANUAL_STOCKS);
        boolean hasIndexes = hasQuote(snapshot.quotes, "^BSESN") && hasQuote(snapshot.quotes, "^NSEI");
        if (snapshot.quotesRetryAt > now) quoteStatus.setText(hasIndexes ? "PARTIAL" : "UNAVAILABLE");
        else if (snapshot.quotesAt == 0) quoteStatus.setText("CONNECTING…");
        else quoteStatus.setText("YAHOO · " + new java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new java.util.Date(snapshot.quotesAt)));
        setFocused(focusedAction, dayTheme);
    }

    void setFocused(int action, boolean dayTheme) {
        focusedAction = action;
        this.dayTheme = dayTheme;
        int focusedHeadline = action >= ACTION_HEADLINE_BASE ? action - ACTION_HEADLINE_BASE : -1;
        newsTitle.setBackground(rounded(action == ACTION_NEWS_SECTION
                ? (dayTheme ? 0xFFE9EFFB : 0xFF343945) : Color.TRANSPARENT, dp(4)));
        for (int i = 0; i < headlineRows.size(); i++)
            styleHeadlineRow(headlineRows.get(i), i, i == focusedHeadline);
        updateActionStyles();
    }

    private void styleHeadlineRow(LinearLayout row, int index) {
        styleHeadlineRow(row, index, false);
    }

    private void styleHeadlineRow(LinearLayout row, int index, boolean focused) {
        row.setBackground(rounded(focused ? 0xFF3549A2
                : index % 2 == 0 ? 0x0C5272B8 : 0x071D2D52, dp(4)));
    }

    private void updateActionStyles() {
        styleToggle(colorToggle, calendarColor, focusedAction == 0);
        styleToggle(darkToggle, calendarDark, focusedAction == 1);
        boolean stockFocused = focusedAction == 2;
        addStock.setBackground(rounded(stockFocused ? (dayTheme ? 0xFF202124 : 0xFFECEEF2)
                : (dayTheme ? 0xFFE9EFFB : 0xFF343945), dp(8)));
        addStock.setTextColor(stockFocused ? (dayTheme ? Color.WHITE : 0xFF18191D) : textColor);
    }

    private void styleToggle(TextView toggle, boolean enabled, boolean focused) {
        int accent = 0xFF3549A2;
        toggle.setText((toggle == colorToggle ? "COLOR " : "DARK ") + (enabled ? "ON" : "OFF"));
        toggle.setTextColor(enabled || focused ? Color.WHITE : calendarMutedColor());
        toggle.setBackground(rounded(enabled || focused ? accent : 0x00000000, dp(5)));
        toggle.setAlpha(enabled || focused ? 1f : 0.78f);
    }

    void toggleCalendarColor() {
        calendarColor = !calendarColor;
        getContext().getSharedPreferences("today", Context.MODE_PRIVATE).edit()
                .putBoolean("calendar_color", calendarColor).apply();
        applyCalendarStyle(0xFF3549A2);
        updateActionStyles();
    }

    void toggleCalendarDark() {
        calendarDark = !calendarDark;
        getContext().getSharedPreferences("today", Context.MODE_PRIVATE).edit()
                .putBoolean("calendar_dark", calendarDark).apply();
        applyCalendarStyle(0xFF3549A2);
        updateActionStyles();
    }

    private void applyCalendarStyle(int accent) {
        int background = calendarDark ? 0xFF202433 : calendarColor ? 0xFFFDFDFF : 0xFFF0F1F4;
        calendar.setBackground(rounded(background, dp(12)));
        int foreground = calendarDark ? 0xFFF5F6FA : 0xFF22243A;
        int muted = calendarDark ? 0xFFB8BED0 : 0xFF5C6075;
        calendarTitle.setTextColor(foreground);
        colorToggle.setTextColor(calendarColor ? accent : muted);
        darkToggle.setTextColor(calendarDark ? accent : muted);
        dayNumber.setTextColor(calendarColor ? accent : foreground);
        weekday.setTextColor(foreground);
        date.setTextColor(muted);
        tithi.setTextColor(foreground);
        nakshatra.setTextColor(foreground);
        varjyam.setTextColor(foreground);
        kalam.setTextColor(muted);
        durmuhurtham.setTextColor(muted);
        festival.setTextColor(Color.WHITE);
        festival.setBackground(rounded(calendarColor ? 0xFF3549A2 : 0xFF626779, dp(5)));
        updateActionStyles();
    }

    private int calendarTextColor() { return calendarDark ? 0xFFF5F6FA : 0xFF22243A; }
    private int calendarMutedColor() { return calendarDark ? 0xFFB8BED0 : 0xFF5C6075; }

    private static String stockName(String symbol) {
        switch (symbol) {
            case "COFORGE.NS": return "Coforge";
            case "JIOFIN.NS": return "Jio Financial";
            case "HDFCBANK.NS": return "HDFC Bank";
            case "RELIANCE.NS": return "Reliance";
            case "TMPV.NS": return "Tata Motors PV";
            default: return symbol;
        }
    }

    private void addQuoteRow(List<Today.Quote> values, String symbol, String label) {
        Today.Quote quote = null;
        for (Today.Quote candidate : values) if (candidate.symbol.equals(symbol)) { quote = candidate; break; }
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(5), 0, dp(5), 0);
        row.setBackground(rounded(symbol.startsWith("^") ? 0x08000000 : 0x0C5272B8, dp(4)));
        TextView name = text(label, 9, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LayoutParams(0, dp(16), 1f));
        String price = quote == null ? "N/A" : money(quote);
        String change = quote == null ? "" : String.format(Locale.ENGLISH, "  %+.2f%%", quote.changePercent);
        TextView value = text(price + change, 9, true);
        value.setSingleLine(true);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (quote != null) value.setTextColor(quote.changePercent < 0 ? 0xFFE06464 : 0xFF238465);
        else value.setTextColor(mutedColor);
        LayoutParams valueParams = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(16));
        valueParams.setMarginStart(dp(5));
        row.addView(value, valueParams);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(16));
        rowLp.topMargin = dp(1);
        quotes.addView(row, rowLp);
    }

    private static boolean hasQuote(List<Today.Quote> values, String symbol) {
        for (Today.Quote quote : values) if (quote.symbol.equals(symbol)) return true;
        return false;
    }

    private static String money(Today.Quote quote) {
        String amount = String.format(Locale.ENGLISH, "%,.2f", quote.price);
        return "INR".equals(quote.currency) ? "₹" + amount : quote.currency + " " + amount;
    }

    private TextView detail(String label) {
        TextView view = text(label, 11, false);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private TextView toggle(Context context, String label) {
        TextView view = text(label, 7, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(3), 0, dp(3), 0);
        return view;
    }

    private LinearLayout.LayoutParams toggleParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(18));
        params.setMarginStart(dp(3));
        return params;
    }

    private TextView timing(Context context, String label) {
        TextView view = text(label, 9, false);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setPadding(dp(5), dp(4), dp(5), dp(4));
        view.setBackground(rounded(0x0C808080, dp(5)));
        return view;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor == 0 ? Color.WHITE : textColor);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        if (!staticViewsReady) paletteTexts.add(view);
        return view;
    }

    private LinearLayout card(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(VERTICAL);
        view.setPadding(dp(14), dp(10), dp(14), dp(8));
        view.setBackground(rounded(0xCC202228, dp(12)));
        return view;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private int dp(float value) { return Math.round(value * density); }
}
