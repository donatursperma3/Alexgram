package tw.nekomimi.nekogram.translate.source.fallback;

public class GoogleTranslatorNeko {

    public static String translate(String text, String from, String to) throws Exception {
        Class<?> translatorClass = Class.forName("app.nekogram.translator.GoogleAppTranslator");
        Object translator = translatorClass.getMethod("getInstance").invoke(null);
        Object result = translatorClass.getMethod("translate", String.class, String.class, String.class).invoke(translator, text, from, to);
        if (result == null) {
            return null;
        }
        try {
            return (String) result.getClass().getField("translation").get(result);
        } catch (NoSuchFieldException e) {
            return (String) result.getClass().getMethod("getTranslation").invoke(result);
        }
    }
}
