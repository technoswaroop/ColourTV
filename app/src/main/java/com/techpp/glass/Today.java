package com.techpp.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** News, market quotes, and the Telugu day view shown on the first home-screen tab. */
final class Today {
    private static final String TAG = "GlassToday";
    private static final long NEWS_REFRESH_MS = 15 * 60_000L, QUOTE_REFRESH_MS = 60_000L, RETRY_MS = 5 * 60_000L;
    static final int MAX_MANUAL_STOCKS = 7;
    private static final String[] NEWS_REGIONS = {"INDIA", "WORLD"};
    private static final String[] NEWS_TOPICS = {"BUSINESS", "FINANCE", "TECH", "POLITICS"};
    private static final String[] INDEXES = {"^BSESN", "^NSEI"};
    private static final String[] WEEKDAYS = {"ఆదివారం", "సోమవారం", "మంగళవారం", "బుధవారం", "గురువారం", "శుక్రవారం", "శనివారం"};
    private static final String[] MONTHS = {"జనవరి", "ఫిబ్రవరి", "మార్చి", "ఏప్రిల్", "మే", "జూన్",
            "జూలై", "ఆగస్టు", "సెప్టెంబర్", "అక్టోబర్", "నవంబర్", "డిసెంబర్"};
    private static final String[] OCTOBER_RAHUKALAM = {
            "సా.4-30ల 6-00", "ఉ.7-30ల 9-00", "మ.3-00ల 4-30", "మ.12-00ల 1-30",
            "మ.1-30ల 3-00", "ఉ.10-30ల 12-00", "ఉ.9-00ల 10-30"
    };
    private static final String[] OCTOBER_DURMUHURTAM = {
            "సా.దు : 4-02ల4-48", "మ.దు : 12-10ల 12-56 · పు.దు : 2-10ల 2-56",
            "ఉ.దు : 8-17ల 9-03 · రా.దు : 10-32ల 11-18", "ప.దు : 11-23ల 12-10",
            "ప.దు : 9-50ల 10-37 · మ.దు : 2-30ల 3-17",
            "ఉ.దు : 8-17ల 9-04 · మ.దు : 12.10ల12.57", "ఉ.దు : 7-29 వరకు"
    };
    private static final String[] YAMAKANTAKAM = {
            "మ.12-00ల 1-30", "ప.10-30ల 12-00", "ఉ.9-00ల 10-30", "ఉ.7-30ల 9-00",
            "ఉ.6-00ల 7-30", "మ.3-00ల 4-30", "మ.1-30ల 3-00"
    };
    private static final String[] DURMUHURTAM = {
            "సా.దు : 3-51ల4-36", "మ.దు : 12-07ల 12-52 · పు.దు : 2-21ల 3-06",
            "ఉ.దు : 8-22ల 9-07 · రా.దు : 10-27ల 11-18", "ప.దు : 11-22ల 12-07",
            "ప.దు : 9-52ల 10-37 · మ.దు : 2-21ల 3-06",
            "ఉ.దు : 8-24ల 9-09 · మ.దు : 12.08ల12.53", "ఉ.దు : 7-37 వరకు"
    };
    private static final String[] NOVEMBER_2026_FESTIVALS = {
            "ఆంధ్రప్రదేశ్ అవతరణ దినోత్సవం, భానుసప్తమి", "", "", "", "మత్రయైకాదశి", "విశాఖకార్తె",
            "ధన్వంతరి జయంతి, ధనత్రయోదశి, మాసశివరాత్రి, నరకచతుర్దశి", "దీపావళి",
            "ఆకాశదీప ప్రా., అమా సోమవార వ్రతం", "అఖండ దీప ప్రా., చంద్రదర్శనం",
            "అన్నాచెల్లెళ్ల పండుగ, భగినీహస్త భోజనం", "", "నాగులచవితి", "నెహ్రూ జయంతి",
            "", "", "వృశ్చిక సంక్రమణం", "", "యాజ్ఞవల్క్య జయంతి",
            "మత్రయైకాదశి, అనూరాధ కార్తె", "క్షీరాబ్ధి ద్వాదశి", "", "",
            "కార్తీక పూర్ణిమ, జ్వాలా తోరణం, గురునానక్ జయంతి", "", "", "సంకటహరచతుర్థి, సుధీంద్రతీర్థ ఆరాధన",
            "", "", ""
    };
    private static final String[] OCTOBER_2026_FESTIVALS = {
            "", "గాంధీజయంతి", "", "", "", "మత్రయైకాదశి", "మాసశివరాత్రి",
            "", "", "మహాలయామావాస్య, కుశగ్రహణం, బతుకమ్మ పండుగ ప్రారంభం",
            "కలశస్థాపనం, దేవీనవరాత్ర ప్రా., చిత్తకార్తె, శరద్ ఋతువు ప్రా.",
            "చంద్రదర్శనం, తిరుమల శ్రీవారి బ్రహ్మోత్సవాలు", "", "",
            "శుక్ర మౌఢ్యమి ప్రా.", "సరస్వతీపూజ", "దేవీత్రిరాత్ర వ్రతం",
            "దుర్గాష్టమి, తులాసంక్రమణం", "మహర్నవమి",
            "విజయదశమి, అపరాజితా దేవిపూజ, శమీపూజ, దుర్గాదేవి విసర్జనం", "",
            "మత్రయైకాదశి", "", "స్వాతికార్తె, శని త్రయోదశి", "", "", "", "అట్లతదియ",
            "సంకటహరచతుర్థి, శుక్రమౌఢ్య త్యాగం", "", ""
    };
    private static final String[] DECEMBER_2026_FESTIVALS = {
            "", "", "జ్యేష్ఠకార్తె", "మత్రయైకాదశి", "", "", "మాసశివరాత్రి", "", "పోలి స్వర్గం, హేమంత ఋతువు ప్రారంభం",
            "చంద్రదర్శనం", "", "", "", "", "సుబ్రహ్మణ్య షష్ఠి", "ధనుస్సంక్రమణం, ధనుర్మాస ప్రా., మూలకార్తె",
            "కాలభైరవాష్టమి", "", "", "మత్రయైకాదశి, ముక్కోటి ఏకాదశి, గీతాజయంతి",
            "సదరు శ్రీ శివానంద మూర్తి గారి జయంతి మహోత్సవం", "హనుమద్వ్రతం", "శ్రీ దత్తజయంతి", "",
            "క్రిస్మస్", "", "సంకటహరచతుర్థి", "", "", "", "అనఘాష్టమి"
    };
    private static final String[][] OCTOBER_2026 = {
            {"బ.పంచమి.2.42", "కృత్తి.ఉ.9.00", "రా.11.59ల1.29"},
            {"బ.షష్ఠి.ప.12.24", "రోహిణి.ఉ.7.29 · మృగ తె.5.52", "ప.12.42ల2.12"},
            {"బ.సప్తమి.ప.10.00", "ఆర్ద్ర తె.4.09", "ప.1.40ల3.09"},
            {"బ.అష్ట.ఉ.7.34 · నవ.తె.5.09", "పునః.రా.2.30", "ప.3.19ల4.48"},
            {"బ.దశ.రా.2.56", "పుష్య.రా.12.59", "ప.10.00ల11.30"},
            {"బ.ఏకా.రా.12.54", "ఆశ్రే.రా.11.40", "ప.1.05ల2.36"},
            {"బ.ద్వాద.రా.11.12", "మఖ.రా.10.40", "ప.11.01ల12.42"},
            {"బ.త్రయో.రా.9.48", "పుబ్బ.రా.9.58", "ఉ.6.26ల7.59 · తె.5.05ల"},
            {"బ.చతు.రా.8.53", "ఉత్తర.రా.9.44", "ఉ.6.41ల"},
            {"అమావాస్య రా.8.23", "హస్త.రా.9.54", "ఉ.6.11ల7.48"},
            {"శు.పాడ్యమి.రా.8.26", "చిత్త.రా.10.38", "ఉ.6.09ల7.48 · తె.4.28ల"},
            {"శు.విదియ.రా.8.56", "స్వాతి.రా.11.46", "ఉ.6.10ల · తె.5.46ల"},
            {"శు.తదియ.రా.10.00", "విశాఖ.రా.1.28", "ఉ.7.28ల · తె.5.48ల"},
            {"శు.చవితి.రా.11.26", "అనూరా.తె.3.30", "ఉ.7.32ల"},
            {"శు.పంచమి.రా.1.17", "జ్యేష్ఠ.సా.5.56", "ప.9.40ల11.26"},
            {"శు.షష్ఠి.రా.3.21", "మూల.పూర్తి", "ప.2.48ల4.34"},
            {"శు.సప్తమి.తె.5.29", "మూల.ఉ.8.31", "ప.6.45ల8.31 · రా.7.10ల8.56"},
            {"శు.అష్టమి పూర్తి", "పూ.షాఢ.ప.11.08", "రా.7.57ల9.43"},
            {"శు.అష్టమి.ఉ.7.27", "ఉ.షాఢ.ప.1.36", "రా.5.57ల7.42"},
            {"శు.నవమి.ఉ.9.10", "శ్రవణ.ఉ.3.46", "రా.8.03ల9.46"},
            {"శు.దశమి.ఉ.10.30", "ధనిష్ఠ.సా.5.33", "రా.1.08ల2.49"},
            {"శు.ఏకాదశి.ఉ.11.21", "శతభిష.రా.6.50", "రా.1.28ల3.07"},
            {"శు.ద్వాద.ఉ.11.43", "పూ.భాద్ర.రా.7.39", "తె.5.22ల"},
            {"శు.త్రయో.ఉ.11.33", "ఉ.భాద్ర.రా.7.56", "ఉ.6.59ల"},
            {"శు.చతుర్దశి.ఉ.10.53", "రేవతి.రా.7.47", "ఉ.7.51ల9.27"},
            {"పౌర్ణమి ఉ.9.47", "అశ్విని.రా.7.11", "ప.3.17ల4.51 · తె.4.24ల5.56"},
            {"బ.పాడ్యమి.ఉ.8.18", "భరణి.రా.6.14", "తె.5.36ల"},
            {"బ.విదియ.ఉ.6.30 · తదియ.తె.4.26", "కృత్తిక.సా.4.58", "ఉ.7.06ల"},
            {"బ.చవితి.రా.2.10", "రోహిణి.3.30", "ఉ.7.59ల9.29 · రా.8.43ల10.13"},
            {"బ.పంచమి.రా.11.46", "మృగశిర.1.53", "రా.9.42ల11.11"},
            {"బ.షష్ఠి.రా.9.21", "ఆర్ద్ర.12.13", "రా.11.23ల12.52"}
    };
    private static final String[][] NOVEMBER_2026 = {
            {"బ.సప్తమి.రా.7.01", "పునః.ఉ.10.33", "రా.6.03ల7.33"},
            {"బ.అష్టమి.సా.4.50", "పుష్య.ఉ.9.02", "రా.9.07ల10.37"},
            {"బ.నవమి.ప.2.52", "ఆశ్రే.ఉ.7.40", "రా.7.07ల8.39"},
            {"బ.దశమి.ప.1.11", "మఖ.ఉ.6.35 · పుబ్బ తె.5.41", "ప.2.17ల3.49"},
            {"బ.ఏకాదశి.ప.11.51", "ఉత్తర.రా.5.21", "ప.12.47ల2.22"},
            {"బ.ద్వాదశి.ప.10.56", "హస్త.రా.5.23", "ప.1.46ల3.22"},
            {"బ.త్రయోదశి.ప.10.30", "చిత్త.తె.6.01", "ప.1.36ల3.14"},
            {"బ.చతుర్దశి.ప.10.34", "స్వాతి.పూర్తి", "ప.11.53ల1.33"},
            {"అమావాస్య ప.11.09", "స్వాతి.ఉ.7.10", "ప.1.09ల2.51"},
            {"శు.పాడ్యమి.ప.12.16", "విశాఖ.ఉ.8.45", "ప.1.05ల2.49"},
            {"శు.విదియ.మ.1.47", "అనూరా.ప.10.44", "సా.4.53ల6.38"},
            {"శు.తదియ.సా.3.40", "జ్యేష్ఠ.ప.1.05", "రా.9.56ల11.42"},
            {"శు.చవితి.రా.5.46", "మూల.3.39", "ప.1.53ల3.39 · రా.2.16ల4.02"},
            {"శు.పంచమి.రా.7.56", "పూ.షాఢ.రా.6.16", "ప.3.05ల4.51"},
            {"శు.షష్ఠి.రా.9.57", "ఉ.షాఢ.రా.8.46", "రా.1.09ల2.54"},
            {"శు.సప్తమి.రా.11.59", "శ్రవణ.రా.11.03", "రా.3.23ల5.07"},
            {"శు.అష్టమి.రా.1.08", "ధనిష్ఠ.రా.12.58", "వర్జ్యము లేదు"},
            {"శు.నవమి.రా.1.58", "శతభిష.2.22", "ఉ.8.35ల10.17"},
            {"శు.దశమి.రా.2.23", "పూ.భాద్ర.3.20", "ఉ.9.02ల10.42"},
            {"శు.ఏకాదశి.రా.2.12", "ఉ.భాద్ర.3.44", "ప.1.06ల2.44"},
            {"శు.ద్వాదశి.రా.1.34", "రేవతి.3.42", "ప.3.43ల5.19"},
            {"శు.త్రయోదశి.రా.12.25", "అశ్విని.3.09", "రా.11.15ల12.49"},
            {"శు.చతుర్దశి.రా.10.57", "భరణి.2.18", "ప.12.26ల1.58"},
            {"పౌర్ణమి రా.9.05", "కృత్తిక.1.05", "ప.1.38ల3.08"},
            {"బ.పాడ్యమి.రా.7.02", "రోహిణి.11.40", "సా.4.09ల5.39 · తె.4.54"},
            {"బ.విదియ.సా.4.47", "మృగశిర.రా.10.04", "ఉ.6.24ల · తె.5.52ల"},
            {"బ.తదియ.2.25", "ఆర్ద్ర.రా.8.23", "ఉ.7.22ల"},
            {"బ.చవితి.11.59", "పునః.రా.6.39", "ఉ.7.32ల9.01 · రా.2.10ల3.40"},
            {"బ.పంచమి.ఉ.9.45", "పుష్య.తె.5.09", "తె.5.12ల"},
            {"బ.షష్ఠి.ఉ.7.36 · సప్త.తె.5.55", "ఆశ్లేష.తె.3.46", "ఉ.6.44ల · రా.3.11ల4.42"}
    };
    private static final String[] DECEMBER_GULIKA = {
            "మ.3-00ల 4-30", "ప.1-30ల 3-00", "ఉ.12-00ల 1-30", "ఉ.10-30ల 12-00",
            "ఉ.9-00ల 10-30", "ఉ.7-30ల 9-00", "ఉ.6-00ల 7-30"
    };
    private static final String[] DECEMBER_DURMUHURTAM = {
            "సా.దు : 3-58ల4-42", "మ.దు : 12-16ల 1-00 · పు.దు : 2-28ల 3-12",
            "ఉ.దు : 8-37ల 9-21 · రా.దు : 10-36ల 11-28", "ప.దు : 11-33ల 12-17",
            "ప.దు : 10-05ల 10-49 · మ.దు : 2-28ల 3-12",
            "ఉ.దు : 8-38ల 9-22 · మ.దు : 12-18ల 1-02", "ఉ.దు : 7-54 వరకు"
    };
    private static final String[][] DECEMBER_2026 = {
            {"బ.అష్టమి.రా.3.55", "మఖ.2.38", "రా.10.20ల11.52"},
            {"బ.నవమి.రా.2.40", "పుబ్బ.1.46", "రా.8.50ల10.24"},
            {"బ.దశమి.రా.1.48", "ఉత్తర.1.20", "రా.9.43ల11.19"},
            {"బ.ఏకాదశి.రా.1.30", "హస్త.1.19", "రా.9.28ల11.06"},
            {"బ.ద్వాదశి.రా.1.34", "చిత్త.1.47", "రా.7.35ల9.15"},
            {"బ.త్రయోదశి.రా.2.15", "స్వాతి.ప.2.44", "రా.8.40ల10.22"},
            {"బ.చతుర్దశి.రా.3.22", "విశాఖ.4.11", "రా.8.30ల10.14"},
            {"అమావాస్య.5.00", "అనూరా.6.05", "రా.12.12ల1.57"},
            {"శు.పాడ్యమి పూర్తి", "జ్యేష్ఠ", "తె.5.12ల"},
            {"శు.పాడ్యమి.ఉ.6.58", "మూల.రా.10.53", "ఉ.6.58ల · రా.9.07ల10.53"},
            {"శు.విదియ.ఉ.9.08", "పూ.షాఢ.రా.1.31", "ప.9.33ల11.19"},
            {"శు.తదియ.11.19", "ఉ.షాఢ.తె.4.07", "ప.10.24ల12.10"},
            {"శు.చవితి.1.21", "శ్రవణ.తె.6.23", "ప.8.30ల10.15"},
            {"శు.పంచమి.3.07", "ధనిష్ఠ పూర్తి", "ప.10.44ల12.28"},
            {"శు.షష్ఠి.4.27", "ధనిష్ఠ.ఉ.8.22", "సా.4.01ల5.43"},
            {"శు.సప్తమి.5.18", "శతభిష.9.53", "రా.12.34ల2.14"},
            {"శు.అష్టమి.సా.5.39", "పూ.భాద్ర.10.55", "రా.8.44ల10.26"},
            {"శు.నవమి.సా.5.30", "ఉ.భాద్ర.11.29", "రా.11.30ల1.06"},
            {"శు.దశమి.సా.4.48", "రేవతి.11.31", "వర్జ్యము లేదు"},
            {"శు.ఏకాదశి.ప.3.43", "అశ్విని.11.09", "రా.8.26ల9.59"},
            {"శు.ద్వాదశి.2.12", "భరణి.10.22", "రా.9.48ల11.20"},
            {"శు.త్రయోదశి.12.22", "కృత్తిక.9.14", "రా.12.20ల1.50"},
            {"శు.చతుర్దశి.10.19", "రోహిణి.7.53 · మృగ.6.22", "ప.1.08ల2.38"},
            {"పౌర్ణమి.8.04 · బ.పాడ్య.5.44", "ఆర్ద్ర.4.41", "ప.2.11ల3.41"},
            {"బ.విదియ.రా.3.21", "పునర్వసు.3.00", "ప.3.51ల5.20"},
            {"బ.తదియ.రా.1.04", "పుష్య.1.23", "ప.10.28ల11.58"},
            {"బ.చవితి.10.57", "ఆశ్లేష.11.57", "ప.1.26ల2.56"},
            {"బ.పంచమి.రా.9.02", "మఖ.10.43", "ప.11.20ల12.51 · తె.6.26ల"},
            {"బ.షష్ఠి.7.28", "పుబ్బ.9.50", "తె.4.52ల6.26"},
            {"బ.సప్తమి.6.13", "ఉత్తర.9.15", "తె.5.38ల"},
            {"బ.అష్టమి.5.28", "హస్త.9.11", "ఉ.7.10ల · తె.5.17ల"}
    };

    static final class Headline {
        final String section, title, description, link, published;
        Headline(String section, String title, String description, String link, String published) {
            this.section = section;
            this.title = title;
            this.description = description;
            this.link = link;
            this.published = published;
        }
    }

    static final class Quote {
        final String symbol, currency;
        final double price, changePercent;
        final long updatedAt;
        Quote(String symbol, String currency, double price, double changePercent, long updatedAt) {
            this.symbol = symbol;
            this.currency = currency;
            this.price = price;
            this.changePercent = changePercent;
            this.updatedAt = updatedAt;
        }

    }

    static final class CalendarDay {
        final int day;
        final String weekday, date, month, tithi, nakshatra, varjyam, kalamName, kalam, durmuhurtham, festival;
        CalendarDay(int day, String weekday, String date, String month, String tithi,
                    String nakshatra, String varjyam, String kalamName, String kalam, String durmuhurtham, String festival) {
            this.day = day;
            this.weekday = weekday;
            this.date = date;
            this.month = month;
            this.tithi = tithi;
            this.nakshatra = nakshatra;
            this.varjyam = varjyam;
            this.kalamName = kalamName;
            this.kalam = kalam;
            this.durmuhurtham = durmuhurtham;
            this.festival = festival;
        }

        boolean hasAlmanac() { return tithi != null; }
    }

    static final class Snapshot {
        final List<Headline> headlines;
        final List<Quote> quotes;
        final List<String> manualSymbols;
        final long quotesAt, headlinesRetryAt, quotesRetryAt;
        Snapshot(List<Headline> headlines, List<Quote> quotes, List<String> manualSymbols, long quotesAt,
                 long headlinesRetryAt, long quotesRetryAt) {
            this.headlines = headlines;
            this.quotes = quotes;
            this.manualSymbols = manualSymbols;
            this.quotesAt = quotesAt;
            this.headlinesRetryAt = headlinesRetryAt;
            this.quotesRetryAt = quotesRetryAt;
        }
    }

    interface Listener { void onTodayUpdated(); }

    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean running;
    private Listener listener;

    Today(Context context) {
        prefs = context.getSharedPreferences("today", Context.MODE_PRIVATE);
    }

    static CalendarDay calendarToday() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR), month = now.get(Calendar.MONTH), day = now.get(Calendar.DAY_OF_MONTH);
        int weekday = now.get(Calendar.DAY_OF_WEEK) - 1;
        String date = new SimpleDateFormat("EEE · MMMM yyyy", Locale.ENGLISH).format(now.getTime());
        String teluguMonth = MONTHS[month];
        if (year != 2026 || month < Calendar.OCTOBER || month > Calendar.DECEMBER) {
            return new CalendarDay(day, WEEKDAYS[weekday], date, teluguMonth,
                    null, null, null, null, null, null, null);
        }
        String[][] days;
        String[] festivals, kalams, durmuhurthams;
        String kalamName;
        if (month == Calendar.OCTOBER) {
            days = OCTOBER_2026;
            festivals = OCTOBER_2026_FESTIVALS;
            kalams = OCTOBER_RAHUKALAM;
            durmuhurthams = OCTOBER_DURMUHURTAM;
            kalamName = "రాహుకాలం";
        } else if (month == Calendar.NOVEMBER) {
            days = NOVEMBER_2026;
            festivals = NOVEMBER_2026_FESTIVALS;
            kalams = YAMAKANTAKAM;
            durmuhurthams = DURMUHURTAM;
            kalamName = "యమకంటక కాలం";
        } else {
            days = DECEMBER_2026;
            festivals = DECEMBER_2026_FESTIVALS;
            kalams = DECEMBER_GULIKA;
            durmuhurthams = DECEMBER_DURMUHURTAM;
            kalamName = "గుళిక కాలం";
        }
        String[] almanac = days[day - 1];
        String festival = festivals[day - 1];
        return new CalendarDay(day, WEEKDAYS[weekday], date, teluguMonth,
                almanac[0], almanac[1], almanac[2], kalamName, kalams[weekday], durmuhurthams[weekday],
                festival.isEmpty() ? null : festival);
    }

    Snapshot cached() {
        return new Snapshot(readHeadlines(), readQuotes(), manualSymbols(),
                prefs.getLong("quotes_at", 0),
                prefs.getLong("headlines_retry_after", 0), prefs.getLong("quotes_retry_after", 0));
    }

    List<String> manualSymbols() {
        String stored = prefs.getString("manual_symbols", "");
        List<String> symbols = new ArrayList<>();
        for (String value : stored.split(",")) {
            String symbol = normalizeSymbol(value);
            if (!symbol.isEmpty() && !symbols.contains(symbol)) symbols.add(symbol);
        }
        String normalized = String.join(",", symbols);
        if (!stored.equals(normalized)) prefs.edit().putString("manual_symbols", normalized).apply();
        return symbols;
    }

    String updateManualSymbols(String input) {
        Set<String> symbols = new LinkedHashSet<>();
        if (input != null && !input.trim().isEmpty()) {
            for (String value : input.split(",")) {
                String symbol = normalizeSymbol(value);
                if (!isValidSymbol(symbol)) return "Use ticker symbols such as RELIANCE.NS or AAPL.";
                symbols.add(symbol);
                if (symbols.size() > MAX_MANUAL_STOCKS)
                    return "You can add up to " + MAX_MANUAL_STOCKS + " stocks.";
            }
        }
        String updated = String.join(",", symbols);
        if (!prefs.getString("manual_symbols", "").equals(updated)) {
            prefs.edit().putString("manual_symbols", updated).putLong("quotes_at", 0)
                    .putLong("quotes_retry_after", 0).apply();
        }
        return null;
    }

    static String normalizeSymbol(String value) {
        String symbol = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return "COFORGE".equals(symbol) ? "COFORGE.NS" : symbol;
    }

    static boolean isValidSymbol(String symbol) {
        return symbol != null && symbol.matches("[A-Z0-9.^=-]{1,15}");
    }

    void refreshIfStale(Listener listener) {
        this.listener = listener;
        long now = System.currentTimeMillis();
        boolean refreshNews = (!hasRequiredHeadlineCoverage() || !hasHeadlineDetails(readHeadlines())
                || now - prefs.getLong("headlines_at", 0) >= NEWS_REFRESH_MS)
                && now >= prefs.getLong("headlines_retry_after", 0);
        boolean refreshQuotes = (now - prefs.getLong("quotes_at", 0) >= QUOTE_REFRESH_MS || !hasEveryQuote())
                && now >= prefs.getLong("quotes_retry_after", 0);
        if ((!refreshNews && !refreshQuotes) || running) return;
        running = true;
        worker.execute(() -> {
            if (refreshNews) {
                try {
                    List<Headline> headlines = fetchHeadlines();
                    long fetchedAt = System.currentTimeMillis();
                    prefs.edit().putString("headlines", serializeHeadlines(headlines))
                            .putLong("headlines_at", fetchedAt)
                            .putLong("headlines_retry_after", hasRequiredHeadlineCoverage(headlines)
                                    && hasHeadlineDetails(headlines) ? 0
                                    : fetchedAt + RETRY_MS).apply();
                } catch (IOException | XmlPullParserException | JSONException e) {
                    Log.w(TAG, "Could not refresh headlines", e);
                    prefs.edit().putLong("headlines_retry_after", System.currentTimeMillis() + RETRY_MS).apply();
                }
            }
            if (refreshQuotes) refreshQuotes();
            main.post(() -> {
                running = false;
                if (this.listener != null) this.listener.onTodayUpdated();
            });
        });
    }

    private boolean hasEveryQuote() {
        Set<String> saved = new LinkedHashSet<>();
        for (Quote quote : readQuotes()) saved.add(quote.symbol);
        for (String symbol : INDEXES) if (!saved.contains(symbol)) return false;
        for (String symbol : manualSymbols()) if (!saved.contains(symbol)) return false;
        return true;
    }

    private void refreshQuotes() {
        List<Quote> quotes = new ArrayList<>(readQuotes());
        boolean complete = true;
        boolean anySuccess = false;
        for (String symbol : allSymbols()) {
            try {
                Quote quote = fetchQuote(symbol);
                replaceQuote(quotes, quote);
                anySuccess = true;
            } catch (IOException | JSONException e) {
                complete = false;
                Log.w(TAG, "Could not refresh market quote for " + symbol, e);
            }
        }
        try {
            long now = System.currentTimeMillis();
            SharedPreferences.Editor edit = prefs.edit().putString("quotes", serializeQuotes(quotes))
                    .putLong("quotes_retry_after", complete ? 0 : now + RETRY_MS);
            if (anySuccess) edit.putLong("quotes_at", now);
            edit.apply();
        } catch (JSONException e) {
            Log.e(TAG, "Could not save market quotes", e);
        }
    }

    private List<String> allSymbols() {
        List<String> symbols = new ArrayList<>();
        for (String symbol : INDEXES) symbols.add(symbol);
        symbols.addAll(manualSymbols());
        return symbols;
    }

    private List<Headline> fetchHeadlines() throws IOException, XmlPullParserException {
        List<Callable<List<Headline>>> requests = new ArrayList<>();
        for (String region : NEWS_REGIONS) {
            for (String topic : NEWS_TOPICS) {
                String section = region + " · " + topic;
                String query = Uri.encode(region + " " + topic);
                String address = "https://news.google.com/rss/search?q=" + query
                        + "&hl=en-IN&gl=IN&ceid=IN:en";
                requests.add(() -> {
                    List<Headline> results = new ArrayList<>();
                    try {
                        readFeed(address, section, results, 3);
                    } catch (IOException | XmlPullParserException e) {
                        Log.w(TAG, "Could not read " + section + " headlines", e);
                    }
                    return results;
                });
            }
        }

        ExecutorService fetchers = Executors.newFixedThreadPool(4);
        List<Headline> headlines = new ArrayList<>();
        List<List<Headline>> categoryResults = new ArrayList<>();
        try {
            List<Future<List<Headline>>> results = fetchers.invokeAll(requests, 12, TimeUnit.SECONDS);
            for (int i = 0; i < results.size(); i++) {
                Future<List<Headline>> result = results.get(i);
                try {
                    categoryResults.add(result.isCancelled() ? new ArrayList<>() : result.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    Log.w(TAG, "Could not collect news category " + i, e.getCause());
                    categoryResults.add(new ArrayList<>());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching news headlines", e);
        } finally {
            fetchers.shutdownNow();
        }

        Set<String> seen = new LinkedHashSet<>();
        for (List<Headline> category : categoryResults) {
            if (!category.isEmpty() && seen.add(category.get(0).title.toLowerCase(Locale.ROOT)))
                headlines.add(category.get(0));
        }
        for (List<Headline> category : categoryResults) {
            for (int i = 1; i < category.size() && headlines.size() < 8; i++) {
                Headline headline = category.get(i);
                if (seen.add(headline.title.toLowerCase(Locale.ROOT))) headlines.add(headline);
            }
        }
        if (headlines.isEmpty()) throw new IOException("News feeds returned no headlines");
        return headlines;
    }

    private boolean hasRequiredHeadlineCoverage() {
        return hasRequiredHeadlineCoverage(readHeadlines());
    }

    private boolean hasRequiredHeadlineCoverage(List<Headline> headlines) {
        Set<String> sections = new LinkedHashSet<>();
        for (Headline headline : headlines) sections.add(headline.section);
        for (String region : NEWS_REGIONS)
            for (String topic : NEWS_TOPICS)
                if (!sections.contains(region + " · " + topic)) return false;
        return true;
    }

    private boolean hasHeadlineDetails(List<Headline> headlines) {
        if (headlines.isEmpty()) return false;
        for (Headline headline : headlines)
            if (headline.description.isEmpty() && headline.link.isEmpty()) return false;
        return true;
    }

    private void readFeed(String address, String section, List<Headline> headlines, int limit)
            throws IOException, XmlPullParserException {
        HttpURLConnection connection = open(address);
        try (InputStream input = connection.getInputStream()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            boolean inItem = false, inTitle = false, inDescription = false, inLink = false, inPubDate = false;
            StringBuilder title = new StringBuilder(), description = new StringBuilder(),
                    link = new StringBuilder(), published = new StringBuilder();
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT && headlines.size() < limit) {
                if (event == XmlPullParser.START_TAG) {
                    if ("item".equals(parser.getName())) {
                        inItem = true;
                        title.setLength(0);
                        description.setLength(0);
                        link.setLength(0);
                        published.setLength(0);
                    } else if (inItem && "title".equals(parser.getName())) inTitle = true;
                    else if (inItem && "description".equals(parser.getName())) inDescription = true;
                    else if (inItem && "link".equals(parser.getName())) inLink = true;
                    else if (inItem && "pubDate".equals(parser.getName())) inPubDate = true;
                } else if ((event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) && inItem && inTitle) {
                    title.append(parser.getText());
                } else if ((event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT)
                        && inItem && inDescription) {
                    description.append(parser.getText());
                } else if ((event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) && inItem && inLink) {
                    link.append(parser.getText());
                } else if ((event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT)
                        && inItem && inPubDate) {
                    published.append(parser.getText());
                } else if (event == XmlPullParser.END_TAG) {
                    if ("title".equals(parser.getName())) inTitle = false;
                    else if ("description".equals(parser.getName())) inDescription = false;
                    else if ("link".equals(parser.getName())) inLink = false;
                    else if ("pubDate".equals(parser.getName())) inPubDate = false;
                    else if ("item".equals(parser.getName())) {
                        String headlineTitle = title.toString().trim();
                        if (!headlineTitle.isEmpty())
                            headlines.add(new Headline(section, headlineTitle, description.toString().trim(),
                                    link.toString().trim(), published.toString().trim()));
                        inItem = false;
                    }
                }
                event = parser.next();
            }
        } finally {
            connection.disconnect();
        }
    }

    private Quote fetchQuote(String symbol) throws IOException, JSONException {
        try {
            return fetchQuoteFrom("query2.finance.yahoo.com", symbol);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Yahoo Finance primary quote endpoint failed for " + symbol + "; trying its alternate endpoint", e);
            return fetchQuoteFrom("query1.finance.yahoo.com", symbol);
        }
    }

    private Quote fetchQuoteFrom(String host, String symbol) throws IOException, JSONException {
        String address = "https://" + host + "/v8/finance/chart/" + Uri.encode(symbol) + "?range=1d&interval=1m";
        HttpURLConnection connection = open(address);
        try (InputStream input = connection.getInputStream()) {
            JSONObject root = new JSONObject(readAll(input));
            JSONObject chart = root.getJSONObject("chart");
            JSONArray result = chart.getJSONArray("result");
            if (result.length() == 0) throw new JSONException("No chart data for " + symbol);
            JSONObject meta = result.getJSONObject(0).getJSONObject("meta");
            double price = meta.optDouble("regularMarketPrice", Double.NaN);
            double previous = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", Double.NaN));
            if (!Double.isFinite(price)) throw new JSONException("No current price for " + symbol);
            double change = Double.isFinite(previous) && previous != 0 ? (price - previous) / previous * 100 : 0;
            long updated = meta.optLong("regularMarketTime", System.currentTimeMillis() / 1000L) * 1000L;
            return new Quote(symbol, meta.optString("currency", "INR"), price, change, updated);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("User-Agent", "Talkies Android TV");
        connection.setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml");
        return connection;
    }

    private static String readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString("UTF-8");
    }

    private List<Headline> readHeadlines() {
        List<Headline> out = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString("headlines", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.getJSONObject(i);
                out.add(new Headline(item.getString("section"), item.getString("title"),
                        item.optString("description", ""), item.optString("link", ""),
                        item.optString("published", "")));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Could not read cached headlines", e);
        }
        return out;
    }

    private List<Quote> readQuotes() {
        List<Quote> out = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString("quotes", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.getJSONObject(i);
                out.add(new Quote(item.getString("symbol"), item.getString("currency"),
                        item.getDouble("price"), item.getDouble("change"), item.getLong("updated")));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Could not read cached market quotes", e);
        }
        return out;
    }

    private static String serializeHeadlines(List<Headline> headlines) throws JSONException {
        JSONArray out = new JSONArray();
        for (Headline headline : headlines) {
            JSONObject item = new JSONObject();
            item.put("section", headline.section);
            item.put("title", headline.title);
            item.put("description", headline.description);
            item.put("link", headline.link);
            item.put("published", headline.published);
            out.put(item);
        }
        return out.toString();
    }

    private static String serializeQuotes(List<Quote> quotes) throws JSONException {
        JSONArray out = new JSONArray();
        for (Quote quote : quotes) {
            JSONObject item = new JSONObject();
            item.put("symbol", quote.symbol);
            item.put("currency", quote.currency);
            item.put("price", quote.price);
            item.put("change", quote.changePercent);
            item.put("updated", quote.updatedAt);
            out.put(item);
        }
        return out.toString();
    }

    private static void replaceQuote(List<Quote> quotes, Quote replacement) {
        for (int i = 0; i < quotes.size(); i++) {
            if (quotes.get(i).symbol.equals(replacement.symbol)) {
                quotes.set(i, replacement);
                return;
            }
        }
        quotes.add(replacement);
    }

}
