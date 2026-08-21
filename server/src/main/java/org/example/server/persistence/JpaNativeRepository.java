package org.example.server.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@Repository
public class JpaNativeRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public int update(String sql, Object... args) {
        Query q = entityManager.createNativeQuery(indexParameters(sql));
        bind(q, args);
        return q.executeUpdate();
    }

    public void execute(String sql) {
        entityManager.createNativeQuery(indexParameters(sql)).executeUpdate();
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        Query q = entityManager.createNativeQuery(indexParameters(sql));
        bind(q, args);
        Object value = q.getSingleResult();
        return convert(value, type);
    }

    public Map<String,Object> queryForMap(String sql, Object... args) {
        List<Map<String,Object>> rows = queryForList(sql, args);
        if (rows.isEmpty()) throw new NoSuchElementException("Query returned no rows");
        return rows.getFirst();
    }

    public List<Map<String,Object>> queryForList(String sql, Object... args) {
        Query q = entityManager.createNativeQuery(indexParameters(sql), Tuple.class);
        bind(q, args);
        @SuppressWarnings("unchecked") List<Tuple> tuples = q.getResultList();
        List<Map<String,Object>> out = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            LinkedHashMap<String,Object> row = new LinkedHashMap<>();
            int i = 0;
            for (TupleElement<?> e : t.getElements()) {
                String alias = e.getAlias();
                if (alias == null || alias.isBlank()) alias = String.valueOf(++i);
                row.put(alias, t.get(e));
            }
            out.add(row);
        }
        return out;
    }

    public <T> List<T> query(String sql, BiFunction<NativeRow,Integer,T> mapper, Object... args) {
        List<NativeRow> rows = rows(sql, args);
        List<T> out = new ArrayList<>(rows.size());
        for (int i=0;i<rows.size();i++) out.add(mapper.apply(rows.get(i), i));
        return out;
    }

    public void query(String sql, Consumer<NativeRow> consumer, Object... args) {
        for (NativeRow row : rows(sql, args)) consumer.accept(row);
    }

    private List<NativeRow> rows(String sql, Object... args) {
        /*
         * Positional row mappers must not depend on Hibernate Tuple element aliases.
         * Hibernate 7 can collapse/omit unaliased native select expressions from a
         * Tuple's element metadata, which made getString(7), getDouble(10), etc.
         * fail even though PostgreSQL returned those columns.
         *
         * Raw native query results preserve the full JDBC column order as Object[].
         * queryForMap/queryForList intentionally keep the Tuple path because those
         * methods need aliases; positional query(...) uses this raw path.
         */
        Query q = entityManager.createNativeQuery(indexParameters(sql), Object[].class);
        bind(q, args);
        @SuppressWarnings("unchecked") List<Object[]> raw = q.getResultList();
        List<NativeRow> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) out.add(new NativeRow(row));
        return out;
    }

    private void bind(Query q, Object... args) {
        if (args == null) return;
        for (int i=0;i<args.length;i++) q.setParameter(i+1, args[i]);
    }

    private String indexParameters(String sql) {
        StringBuilder out = new StringBuilder(sql.length()+16);
        boolean single = false;
        int index = 1;
        for (int i=0;i<sql.length();i++) {
            char c = sql.charAt(i);
            if (c=='\'' && (i==0 || sql.charAt(i-1)!='\\')) single = !single;
            if (c=='?' && !single) out.append('?').append(index++); else out.append(c);
        }
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(Object value, Class<T> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return (T)value;
        if (type == String.class) return (T)String.valueOf(value);
        if (value instanceof Number n) {
            if (type == Integer.class || type == int.class) return (T)Integer.valueOf(n.intValue());
            if (type == Long.class || type == long.class) return (T)Long.valueOf(n.longValue());
            if (type == Double.class || type == double.class) return (T)Double.valueOf(n.doubleValue());
            if (type == BigDecimal.class) return (T)new BigDecimal(n.toString());
        }
        return type.cast(value);
    }

    public static final class NativeRow {
        private final List<Object> values = new ArrayList<>();
        private final Map<String,Object> aliases = new LinkedHashMap<>();
        NativeRow(Tuple tuple) {
            for (TupleElement<?> e : tuple.getElements()) {
                Object v = tuple.get(e); values.add(v);
                if (e.getAlias()!=null) aliases.put(e.getAlias().toLowerCase(Locale.ROOT), v);
            }
        }
        NativeRow(Object row) {
            if (row instanceof Object[] array) {
                values.addAll(Arrays.asList(array));
            } else if (row instanceof Tuple tuple) {
                for (TupleElement<?> e : tuple.getElements()) {
                    Object v = tuple.get(e);
                    values.add(v);
                    if (e.getAlias()!=null) aliases.put(e.getAlias().toLowerCase(Locale.ROOT), v);
                }
            } else {
                values.add(row);
            }
        }
        private Object value(int index){
            if (index < 1 || index > values.size()) {
                throw new IllegalStateException("Native query row requested column " + index
                    + " but PostgreSQL returned " + values.size() + " column(s)");
            }
            return values.get(index-1);
        }
        private Object value(String key){ return aliases.get(key.toLowerCase(Locale.ROOT)); }
        public Object getObject(int index){ return value(index); }
        public Object getObject(String key){ return value(key); }
        public String getString(int index){ return Objects.toString(value(index), null); }
        public String getString(String key){ return Objects.toString(value(key), null); }
        public int getInt(int index){ Object v=value(index); return v==null?0:((Number)v).intValue(); }
        public int getInt(String key){ Object v=value(key); return v==null?0:((Number)v).intValue(); }
        public long getLong(int index){ Object v=value(index); return v==null?0L:((Number)v).longValue(); }
        public long getLong(String key){ Object v=value(key); return v==null?0L:((Number)v).longValue(); }
        public double getDouble(int index){ Object v=value(index); return v==null?0d:((Number)v).doubleValue(); }
        public double getDouble(String key){ Object v=value(key); return v==null?0d:((Number)v).doubleValue(); }
        public boolean getBoolean(int index){ return asBoolean(value(index)); }
        public boolean getBoolean(String key){ return asBoolean(value(key)); }
        private static boolean asBoolean(Object value){
            if(value==null) return false;
            if(value instanceof Boolean b) return b;
            if(value instanceof Number n) return n.doubleValue()!=0d;
            String text=String.valueOf(value).trim();
            return "true".equalsIgnoreCase(text)||"t".equalsIgnoreCase(text)||"yes".equalsIgnoreCase(text)||"y".equalsIgnoreCase(text)||"1".equals(text);
        }
        public NativeMetaData getMetaData(){ return new NativeMetaData(values.size()); }
    }
    public record NativeMetaData(int columnCount) { public int getColumnCount(){ return columnCount; } }
}
