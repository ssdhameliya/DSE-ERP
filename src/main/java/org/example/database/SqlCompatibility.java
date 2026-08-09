package org.example.database;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translates the remaining legacy SQLite expressions at the JDBC boundary. */
final class SqlCompatibility {
    private static final Pattern RELATIVE_DATE = Pattern.compile(
            "date\\('now','([+-]\\d+) day'\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_FUNCTION = Pattern.compile(
            "date\\(([a-zA-Z_][a-zA-Z0-9_.]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRINTF_MONEY = Pattern.compile(
            "printf\\('%?,?\\.2f',([a-zA-Z_][a-zA-Z0-9_.]*)\\)", Pattern.CASE_INSENSITIVE);

    private SqlCompatibility() {}

    static Connection wrap(Connection connection) {
        return wrap(connection, true);
    }

    static Connection wrap(Connection connection, boolean closeable) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (!closeable && method.getName().equals("close")) return null;
                    Object[] translated = translateFirstSqlArgument(args);
                    Object result = invoke(connection, method, translated);
                    return result instanceof Statement statement ? wrapStatement(statement) : result;
                });
    }

    static Connection nonClosing(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    return invoke(connection, method, args);
                });
    }

    static String translate(String sql) {
        String result = sql;
        result = result.replaceAll("(?i)datetime\\('now'\\)", "CURRENT_TIMESTAMP");
        result = result.replaceAll("(?i)date\\('now'\\)", "CURRENT_DATE");
        result = result.replace(
                "date('now','start of month','-' || ((cast(strftime('%m','now') as integer)-1)%3) || ' months')",
                "date_trunc('quarter',CURRENT_DATE)::date");
        result = result.replaceAll("(?i)strftime\\('%Y-%m','now'\\)", "to_char(CURRENT_DATE,'YYYY-MM')");
        result = result.replaceAll("(?i)strftime\\('%Y','now'\\)", "to_char(CURRENT_DATE,'YYYY')");
        result = result.replace("GLOB 'PUR-[0-9]*'", "~ '^PUR-[0-9]+$'");
        result = result.replaceAll("(?i)date\\('now','start of month'\\)", "date_trunc('month',CURRENT_DATE)::date");
        result = result.replaceAll("(?i)date\\('now','start of year'\\)", "date_trunc('year',CURRENT_DATE)::date");

        Matcher relative = RELATIVE_DATE.matcher(result);
        StringBuffer dates = new StringBuffer();
        while (relative.find()) {
            int days = Integer.parseInt(relative.group(1));
            relative.appendReplacement(dates, Matcher.quoteReplacement(
                    "(CURRENT_DATE " + (days < 0 ? "-" : "+") + " INTERVAL '" + Math.abs(days) + " days')"));
        }
        relative.appendTail(dates);
        result = dates.toString();

        Matcher dateFunction = DATE_FUNCTION.matcher(result);
        result = dateFunction.replaceAll("CAST($1 AS DATE)");
        Matcher printf = PRINTF_MONEY.matcher(result);
        result = printf.replaceAll("to_char($1,'FM999999999990.00')");

        if (result.regionMatches(true, 0, "INSERT OR IGNORE INTO", 0, 21)) {
            result = "INSERT INTO" + result.substring(21);
            if (result.endsWith(";")) result = result.substring(0, result.length() - 1);
            result += " ON CONFLICT DO NOTHING";
        }
        return result;
    }

    private static Statement wrapStatement(Statement statement) {
        Class<?> api = statement instanceof java.sql.PreparedStatement
                ? java.sql.PreparedStatement.class : Statement.class;
        return (Statement) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api},
                (proxy, method, args) -> invoke(statement, method, translateFirstSqlArgument(args)));
    }

    private static Object[] translateFirstSqlArgument(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof String sql)) return args;
        Object[] copy = args.clone();
        copy[0] = translate(sql);
        return copy;
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
