package persist;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import jLib.*;
import jLib.Lib;



/**
 * A way to create unlimited-size persistent Maps and Lists.
 * - Lists can act like a dequeue, i.e. add and remove from either end efficiently.
 * - Lists and Map sizes can exceed Integer.MAX_VALUE.
 * - Entire Lists and Maps are not normally ever loaded into memory.
 */
public class PersistentData implements AutoCloseable {
    private static final long R00T_ID = -1000;
    private static final long R00T_PARENT_ID = -9999;
    private final String tableName;
    private final Connection conn;
    public final ConcurrentLinkedDeque<Runnable> beforeClose = new ConcurrentLinkedDeque<>();
    public final ConcurrentLinkedDeque<Runnable> afterClose = new ConcurrentLinkedDeque<>();
    public static String defaultJdbcUrl = "jdbc:hsqldb:file:./datafiles/dbf/PersistentData";



    public PersistentData() {
        this( defaultJdbcUrl, "PersistentData" );
    }
    public PersistentData( String jdbcUrl, String tableName ) {
        this.tableName = tableName;
        try {
            if ( jdbcUrl.startsWith("jdbc:hsqldb:") ) {
                if (jdbcUrl.startsWith("jdbc:hsqldb:file:")) {
                    conn = DriverManager.getConnection(jdbcUrl + ";shutdown=true", "SA", "");
                } else {
                    conn = DriverManager.getConnection(jdbcUrl, "SA", "");
                }
                try ( Statement stmt = conn.createStatement() ) {
                    stmt.execute( "SET DATABASE SQL SYNTAX ORA TRUE" );
                    stmt.execute( "SET DATABASE TRANSACTION CONTROL MVCC" );
                    stmt.execute( "SET FILES WRITE DELAY FALSE" );
                }
            } else if ( jdbcUrl.startsWith("jdbc:sqlite:") ) {
                conn = DriverManager.getConnection(jdbcUrl);
                try ( Statement stmt = conn.createStatement() ) {
                    stmt.execute( Lib.nw( """
                        pragma journal_mode = WAL;
                        pragma synchronous = normal;
                        pragma temp_store = memory;
                        pragma mmap_size = 2000000000;
                    """ ) );
                }
            } else {
                conn = DriverManager.getConnection(jdbcUrl);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        createTable();
    }



    public PersistentData construct( String jdbcUrl, String tableName ) {
        return new PersistentData(jdbcUrl,tableName);
    }
    public static PersistentData construct( File dataFile, String tableName ) {
        String jdbcUrl = null;
        String path = dataFile.getAbsolutePath();
        if (path.endsWith(".db")) path = path.substring(0, path.length()-3);
        try{ jdbcUrl = "jdbc:hsqldb:file:"+dataFile.getCanonicalFile().getAbsolutePath(); }
        catch (IOException e) { jdbcUrl = "jdbc:hsqldb:file:"+path; }
        return new PersistentData(jdbcUrl,tableName);
    }



    public static PersistentData temp( String tableName ) {
        File tmpFile = new File( Lib.tmpDir(), "temp_" + System.currentTimeMillis() );
        String jdbcUrl = null;
        try{ jdbcUrl = "jdbc:hsqldb:mem:"+tmpFile.getName(); }
        catch (Exception e) { jdbcUrl = "jdbc:hsqldb:mem:temp"; }
        PersistentData pd = new PersistentData(jdbcUrl,tableName);
        return pd;
    }
    public static PersistentData temp() { return temp("temp"); }



    public class Row {
        public final Long id;
        public final Long parentID;
        public final Long entryOrder;
        public final String keyJson;
        public final String valueJson;
        public final Long lastChangeTime;
        public Row( Long id, Long parentID, Long entryOrder, String keyJson, String valueJson, Long lastChangeTime ) {
            this.id = id;
            this.parentID = parentID;
            this.entryOrder = entryOrder;
            this.keyJson = keyJson;
            this.valueJson = valueJson;
            this.lastChangeTime = lastChangeTime;
        }
        public Row( long id ) {
            this(id,null,null,null,null,null);
        }
        public Row( long parentID, long entryOrder ) {
            this(null,parentID,entryOrder,null,null,null);
        }
        public Row( long parentID, String keyJson ) {
            this(null,parentID,null,keyJson,null,null);
        }
        public Row insert() {
            String sql = Lib.evalTemplate( Lib.unindent( """
                INSERT INTO {{tableName}} (
                    id, parent_id, entry_order, key_json, value_json, last_change_time
                ) VALUES (?,?,?,?,?,?)
            """ ), Map.of( "tableName", tableName ) );
            try {
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setLong(1, id);
                stmt.setLong(2, parentID);
                stmt.setLong(3, entryOrder);
                stmt.setString(4, keyJson);
                stmt.setString(5, valueJson);
                stmt.setLong(6, lastChangeTime);
                return stmt.executeUpdate()==1 ? this : null;
            } catch (SQLException e) { throw new RuntimeException(e); }
        }
        public Row update( Map<String,Object> newValues ) {
            return update( new Row(
                (Long)newValues.get("id"),
                (Long)newValues.get("parentID"),
                (Long)newValues.get("entryOrder"),
                (String)newValues.get("keyJson"),
                (String)newValues.get("valueJson"),
                (Long)newValues.get("lastChangeTime")
            ) );
        }
        public Row update( Row newValues ) {
            List<String> setSql = new ArrayList<>();
            List<Object> setValues = new ArrayList<>();
            List<String> whereSql = new ArrayList<>();
            List<Object> whereValues = new ArrayList<>();
            long changeTime = Lib.currentTimeMicros();
            final Row row = this;
            if (row.id!=null) {
                whereSql.add("id=?");
                whereValues.add(row.id);
            } else {
                if (row.parentID==null) throw new IllegalArgumentException("id and parent_id cannot both be null");
                if (row.entryOrder==null && row.keyJson==null) throw new IllegalArgumentException(
                    "id and parent_id cannot both be null"
                );
                whereSql.add("parent_id=?");
                whereValues.add(row.parentID);
                if (row.entryOrder!=null) {
                    whereSql.add("entry_order=?");
                    whereValues.add(row.entryOrder);
                }
                if (row.keyJson!=null) {
                    whereSql.add("key_json=?");
                    whereValues.add(row.keyJson);
                }
            }
            if (newValues.id!=null) {
                setSql.add("id=?");
                setValues.add(newValues.id);
            }
            if (newValues.parentID!=null) {
                setSql.add("parent_id=?");
                setValues.add(newValues.parentID);
            }
            if (newValues.entryOrder!=null) {
                setSql.add("entry_order=?");
                setValues.add(newValues.entryOrder);
            }
            if (newValues.keyJson!=null) {
                setSql.add("key_json=?");
                setValues.add(newValues.keyJson);
            }
            if (newValues.valueJson!=null) {
                setSql.add("value_json=?");
                setValues.add(newValues.valueJson);
            }
            {
                setSql.add("last_change_time=?");
                setValues.add(changeTime);
            }
            if (setSql.isEmpty()) return null;
            String sql = (
                "UPDATE "+tableName+" SET "+String.join(",",setSql)
                +" WHERE "+String.join(" AND ",whereSql)
            );
            List<Object> allValues = new ArrayList<>(setValues);
            allValues.addAll(whereValues);
            try ( PreparedStatement stmt = conn.prepareStatement(sql) ) {
                for ( int i=0; i<allValues.size(); i++ ) {
                    Object value = allValues.get(i);
                    if ( value instanceof String ) stmt.setString( i+1, (String)value );
                    if ( value instanceof Long ) stmt.setLong( i+1, (Long)value );
                }
                boolean success = stmt.executeUpdate() == 1;
                if (!success) return null;
            }
            catch (SQLException e) { throw new RuntimeException(e); }
            Row newRow = new Row(
                newValues.id!=null ? newValues.id : row.id,
                newValues.parentID!=null ? newValues.parentID : row.parentID,
                newValues.entryOrder!=null ? newValues.entryOrder : row.entryOrder,
                newValues.keyJson!=null ? newValues.keyJson : row.keyJson,
                newValues.valueJson!=null ? newValues.valueJson : row.valueJson,
                changeTime
            );
            return newRow;
        }
        public Row select() {
            PreparedStatement pstmt = null;
            String sql;
            try {
                if (id!=null) {
                    sql = Lib.evalTemplate( Lib.unindent( """
                        SELECT * FROM {{tableName}} WHERE id=?
                    """ ), Map.of( "tableName", tableName ) );
                    pstmt = conn.prepareStatement(sql);
                    pstmt.setLong(1, id);
                } else if (parentID==null) {
                    throw new IllegalArgumentException("id and parent_id cannot both be null");
                } else if (entryOrder!=null) {
                    sql = Lib.evalTemplate( Lib.unindent( """
                        SELECT * FROM {{tableName}} WHERE parent_id=? AND entry_order=?
                    """ ), Map.of( "tableName", tableName ) );
                    pstmt = conn.prepareStatement(sql);
                    pstmt.setLong(1, parentID);
                    pstmt.setLong(2, entryOrder);
                } else if (keyJson!=null) {
                    sql = Lib.evalTemplate( Lib.unindent( """
                        SELECT * FROM {{tableName}} WHERE parent_id=? AND key_json=?
                    """ ), Map.of( "tableName", tableName ) );
                    pstmt = conn.prepareStatement(sql);
                    pstmt.setLong(1, parentID);
                    pstmt.setString(2, keyJson);
                } else {
                    throw new IllegalArgumentException("need id or parentID+entryOrder or parentID+keyJson");
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) return null;
                    return new Row(
                        rs.getLong("id"),
                        rs.getLong("parent_id"),
                        rs.getLong("entry_order"),
                        rs.getString("key_json"),
                        rs.getString("value_json"),
                        rs.getLong("last_change_time")
                    );
                } finally { if (pstmt!=null) pstmt.close(); }
            } catch (SQLException e) { throw new RuntimeException(e); }
        }
        public Row delete() {
            Row deletedRow = select();
            if (deletedRow==null) return null;
            try {
                String sql = Lib.evalTemplate( Lib.unindent( """
                    DELETE FROM {{tableName}} WHERE id=?
                """ ), Map.of( "tableName", tableName ) );
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setLong(1, deletedRow.id);
                boolean success = stmt.executeUpdate() == 1;
                return success ? deletedRow : null;
            } catch (SQLException e) { throw new RuntimeException(e); }
        }
        public Map<String,Object> toMap() {
            return Lib.mapOf(
                "id", id,
                "parentID", parentID,
                "entryOrder", entryOrder,
                "keyJson", keyJson,
                "valueJson", valueJson,
                "lastChangeTime", lastChangeTime
            );
        }
        public String toString() {
            return JsonEncoder.encode( toMap() );
        }
    }
    public Row row( long id ) { return new Row(id); }
    public Row row( long parentID, long entryOrder ) { return new Row(parentID,entryOrder); }
    public Row row( long parentID, String keyJson ) { return new Row(parentID,keyJson); }
    public Row row( Long id, Long parentID, Long entryOrder, String keyJson, String valueJson, Long lastChangeTime ) {
        return new Row(id,parentID,entryOrder,keyJson,valueJson,lastChangeTime);
    }



    /**
     * NOTE: if this represents a map or a list, then value_json will be "MAP" or "LIST",
     * but not quoted, so it will be invalid json.
    */
    private void createTable() {
        String textType = "TEXT";
        try {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            if (dbProduct.toLowerCase().contains("hsql")) {
                textType = "VARCHAR(32672)";
            }
        } catch (SQLException ignore) {}

        String createTable = Lib.evalTemplate( Lib.unindent( """
            CREATE TABLE IF NOT EXISTS {{tableName}} (
                id BIGINT NOT NULL,
                parent_id BIGINT NOT NULL,
                entry_order BIGINT NOT NULL,
                key_json {{textType}} NOT NULL,
                value_json {{textType}} NOT NULL,
                last_change_time BIGINT NOT NULL,
                PRIMARY KEY (id)
            )
        """ ), Map.of( "tableName", this.tableName, "textType", textType ) );
        String createIndexA = Lib.evalTemplate( Lib.unindent( """
            CREATE INDEX IF NOT EXISTS {{tableName}}_key_idx
            ON {{tableName}} (parent_id, key_json)
        """ ), Map.of( "tableName", this.tableName ) );
        String createIndexB = Lib.evalTemplate( Lib.unindent( """
            CREATE INDEX IF NOT EXISTS {{tableName}}_order_idx  -- this would be UNIQUE except it would be
            ON {{tableName}} (parent_id, entry_order)           -- violated temporarily during overlapping updates
        """ ), Map.of( "tableName", this.tableName ) );
        try ( Statement stmt = conn.createStatement() ) {
            stmt.execute(createTable);
            stmt.execute(createIndexA);
            stmt.execute(createIndexB);
        }
        catch ( SQLException e ) { throw new RuntimeException(e); }
    }



    public PersistentMap getRootMap() {
        long changeTime = Lib.currentTimeMicros();
        Row row = new Row(R00T_ID).select();
        if (row==null) {
            long parentID = R00T_PARENT_ID;
            long entryOrder = 0L;
            String keyJson = "ROOT";
            String valueJson = "MAP";
            row = new Row( R00T_ID, parentID, entryOrder, keyJson, valueJson, changeTime ).insert();
        }
        return getMap(R00T_ID);
    }
    public PersistentList getRootList() {
        long changeTime = Lib.currentTimeMicros();
        Row row = new Row(R00T_ID).select();
        if (row==null) {
            long parentID = R00T_PARENT_ID;
            long entryOrder = 0L;
            String keyJson = "ROOT";
            String valueJson = "LIST";
            row = new Row( R00T_ID, parentID, entryOrder, keyJson, valueJson, changeTime ).insert();
        }
        return getList(R00T_ID);
    }
    public PersistentMap getMap( long parentID ) {
        long changeTime = Lib.currentTimeMicros();
        Row row = new Row(parentID).select();
        if (row==null) return null;
        if (! "MAP".equals(row.valueJson) ) {
            row.update( Lib.mapOf( "valueJson","MAP", "lastChangeTime",changeTime ) );
        }
        PersistentMap map = (PersistentMap) get(row.id);
        return map;
    }
    public PersistentList getList( long parentID ) {
        long changeTime = Lib.currentTimeMicros();
        Row row = new Row(parentID).select();
        if (row==null) return null;
        if (! "LIST".equals(row.valueJson) ) {
            row = row.update( Lib.mapOf( "valueJson","LIST", "lastChangeTime",changeTime ) );
        }
        PersistentList list = (PersistentList) get(row.id);
        return list;
    }



    public long size( long parentID ) {
        long[] minMaxEntryOrder = getMinMaxEntryOrder(parentID);
        if ( minMaxEntryOrder==null ) return 0L;
        return 1 + minMaxEntryOrder[1] - minMaxEntryOrder[0];
    }



    public Object get( long id ) {
        Row row = row(id).select();
        if (row==null) return null;
        if ("MAP".equals(row.valueJson) ) return new PersistentMap( this , row.id );
        if ("LIST".equals(row.valueJson) ) return new PersistentList( this, row.id );
        return JsonDecoder.decode(row.valueJson);
    }
    public Object get( long parentID, long entryOrder ) {
        Row row = row(parentID,entryOrder).select();
        if (row==null) return null;
        return get(row.id);
    }
    public Object get( long parentID, String keyJson ) {
        Row row = row(parentID,keyJson).select();
        if (row==null) return null;
        return get(row.id);
    }



    /**
     * note: returns NEW persistant version of value; not old replaced value
     */
    public Object put( long id, Object value ) {
        long changeTime = Lib.currentTimeMicros();
        if ( value instanceof Map<?,?> ) {
            for ( Object key : ((Map<?,?>)value).keySet() ) {
                Object val = ((Map<?,?>)value).get(key);
                String keyJson = JsonEncoder.encode(key);
                put( id, keyJson, val );
            }
            return getMap(id);
        }
        if ( value instanceof List<?> ) {
            long[] minMaxEntryOrder = getMinMaxEntryOrder(id);
            long entryOrder = minMaxEntryOrder==null ? 0L : minMaxEntryOrder[0];
            for ( Object val : ((List<?>)value) ) {
                put( id, entryOrder, val );
                entryOrder++;
            }
            return getList(id);
        }
        String valueJson = JsonEncoder.encode(value);
        row(id).update( Lib.mapOf( "valueJson",valueJson, "lastChangeTime",changeTime ) );
        return value;
    }
    public Object put( long parentID, String keyJson, Object value ) {
        long changeTime = Lib.currentTimeMicros();
        String valueJson = (
            ( value instanceof Map<?,?> ) ? "MAP" : ( value instanceof List<?> ) ? "LIST" : JsonEncoder.encode(value)
        );
        Row row = row(parentID,keyJson).select();
        if (row==null) {
            long[] minMaxEntryOrder=getMinMaxEntryOrder(parentID);
            long newID = changeTime;
            long entryOrder = minMaxEntryOrder==null ? 0 : minMaxEntryOrder[1]+1;
            row = new Row(newID,parentID,entryOrder,keyJson,valueJson,changeTime).insert();
        } else {
            row.update(Lib.mapOf( "valueJson",valueJson, "lastChangeTime",changeTime ));
        }
        if ( value instanceof Map<?,?> ) return put( row.id, value );
        if ( value instanceof List<?> ) return put( row.id, value );
        return value;
    }
    public Object put( long parentID, long entryOrder, Object value ) {
        long changeTime = Lib.currentTimeMicros();
        { // make sure entryOrder is contiguous
            long[] minMaxEntryOrder=getMinMaxEntryOrder(parentID);
            if (minMaxEntryOrder!=null) {
                entryOrder = Math.max( minMaxEntryOrder[0]-1, entryOrder );
                entryOrder = Math.min( minMaxEntryOrder[1]+1, entryOrder );
            }
        }
        String valueJson = (
            ( value instanceof Map<?,?> ) ? "MAP" : ( value instanceof List<?> ) ? "LIST" : JsonEncoder.encode(value)
        );
        Row row = row(parentID,entryOrder).select();
        if (row==null) {
            long newID = changeTime;
            String keyJson = Lib.dblQuot( Lib.uniqID() );
            row = new Row(newID,parentID,entryOrder,keyJson,valueJson,changeTime).insert();
        } else {
            row.update(Lib.mapOf( "valueJson",valueJson, "lastChangeTime",changeTime ));
        }
        if ( value instanceof Map<?,?> ) return put( row.id, value );
        if ( value instanceof List<?> ) return put( row.id, value );
        return value;
    }



    /**
     * NOTE: returns valueJson of deleted row
     */
    public String remove( long id ) {
        Row row = row(id).select();
        if (row==null) return null;
        return remove( row.parentID, row.entryOrder );
    }
    public String remove( long parentID, long entryOrder ) {
        Row row = row(parentID,entryOrder).delete();
        if (row==null) return null;
        removeGap(parentID,entryOrder,entryOrder);
        return row.valueJson;
    }
    public String remove( long parentID, String keyJson ) {
        Row row = row(parentID,keyJson).select();
        if (row==null) return null;
        return remove( row.parentID, row.entryOrder );
    }



    /**
     * NOTE: returns the NEW persistent version of value
     */
    public Object insert( long parentID, long entryOrder, Object value ) {
        long[] minMaxEntryOrder=getMinMaxEntryOrder(parentID);
        if (minMaxEntryOrder==null) return put(parentID,value);
        if (
            minMaxEntryOrder==null || entryOrder<minMaxEntryOrder[0] || entryOrder>minMaxEntryOrder[1]
        ) { // not an insert
            return put( parentID, entryOrder, value );
        }
        if ( entryOrder==minMaxEntryOrder[0] ) return put( parentID, entryOrder-1, value ); // insert at top
        if ( entryOrder==minMaxEntryOrder[1] ) return put( parentID, entryOrder+1, value ); // insert at bottom
        createGap(parentID,entryOrder,1L);
        return put(parentID,entryOrder,value);
    }



    @SuppressWarnings("unused")
    private static boolean dml_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        try ( PersistentData pd = PersistentData.temp() ) {
            { // test insert
                Object testData = JsonDecoder.decode( """
                    { "ONE": 1, "TWO": [1,2], "THREE": [1,2,3] }
                """);
                pd.put( R00T_ID, testData );
                Object actualObject = pd.getRootMap();
                String expectJson = JsonEncoder.encode(testData);
                String actualJson = JsonEncoder.encode(actualObject);
                Lib.asrtEQ(expectJson,actualJson);
                Lib.asrtEQ( pd.size(R00T_ID), 3 );
            }
            { // test replace, even with different type
                pd.put( R00T_ID, Lib.dblQuot("ZERO"), 0 );
                pd.put( R00T_ID, Lib.dblQuot("ONE"), List.of(0,1) );
                pd.put( R00T_ID, Lib.dblQuot("TWO"), List.of(0,1,2) );
                pd.put( R00T_ID, Lib.dblQuot("THREE"), List.of(0,1,2,3) );
                pd.put( R00T_ID, Lib.dblQuot("FOUR"), List.of(0,1,2,3,4) );
                Object actualObject = pd.getRootMap();
                Object expectObject = JsonDecoder.decode("""
                    {"ZERO":0,"ONE":[0,1],"TWO":[0,1,2],"THREE":[0,1,2,3],"FOUR":[0,1,2,3,4]}
                """);
                Lib.asrtEQ(expectObject,actualObject);
                Lib.asrtEQ( pd.size(R00T_ID), 5 );
            }
            { // test delete
                pd.remove( R00T_ID, Lib.dblQuot("ZERO") );
                pd.remove( R00T_ID, Lib.dblQuot("FOUR") );
                Object actualObject = pd.getRootMap();
                Object expectObject = JsonDecoder.decode("""
                    {"ONE":[0,1],"TWO":[0,1,2],"THREE":[0,1,2,3]}
                """);
                Lib.asrtEQ(expectObject,actualObject);
                Lib.asrtEQ( pd.size(R00T_ID), 3 );
            }
        }
        return true;
    }



    public long[] getMinMaxEntryOrder( long parentID ) {
        /* NOTE: can't use this because SQLITE returns 0,0 on an empty table!
            String sql = Lib.evalTemplate( Lib.unindent( """
                SELECT MIN(entry_order) AS min_entry_order, MAX(entry_order) AS max_entry_order
                FROM {{tableName}} WHERE parent_id=?
            """ ), Map.of( "tableName", this.tableName ) );
        */
        String sql = Lib.evalTemplate( Lib.unindent( """
            SELECT entry_order FROM {{tableName}} WHERE parent_id=?
            AND entry_order = (SELECT MIN(entry_order) FROM {{tableName}} WHERE parent_id=?)
                UNION ALL
            SELECT entry_order FROM {{tableName}} WHERE parent_id=?
            AND entry_order = (SELECT MAX(entry_order) FROM {{tableName}} WHERE parent_id=?)
        """ ), Map.of( "tableName", this.tableName ) );
        try ( PreparedStatement stmt = conn.prepareStatement(sql) ) {
            stmt.setLong(1, parentID);
            stmt.setLong(2, parentID);
            stmt.setLong(3, parentID);
            stmt.setLong(4, parentID);
            try ( ResultSet rs = stmt.executeQuery() ) {
                if (! rs.next() ) return null;
                long minEntryOrder = rs.getLong("entry_order");
                if (! rs.next() ) return null;
                long maxEntryOrder = rs.getLong("entry_order");
                return new long[] { minEntryOrder, maxEntryOrder };
            }
        }
        catch (SQLException e) { throw new RuntimeException(e); }
    }



    public Iterator<Row> rowIterator( long parentID ) {
        long[] minMaxEntryOrder = getMinMaxEntryOrder(parentID);
        if (minMaxEntryOrder==null) return Collections.emptyIterator();
        final long firstEntryOrder = minMaxEntryOrder[0];
        return new Iterator<Row>() {
            private Row nextRow = new Row(parentID,firstEntryOrder).select();
            private Row prevRow = null;
            @Override public boolean hasNext() { return nextRow!=null; }
            @Override public Row next() {
                if (nextRow==null) throw new NoSuchElementException();
                prevRow = nextRow;
                nextRow = new Row( parentID, nextRow.entryOrder+1 ).select();
                return prevRow;
            }
            @Override public void remove() {
                if (prevRow==null) throw new IllegalStateException("no previous row");
                PersistentData.this.remove( parentID, prevRow.entryOrder );
            }
        };
    }
    public Iterable<Row> rowIterable( long parentID ) {
        return Lib.asIterable( rowIterator(parentID) );
    }



    /**
     * NOTE: not recursive; can leave orphan rows
     */
    public long clearChildValues( long parentID ) {
        String sql = Lib.evalTemplate( Lib.unindent( """
            DELETE FROM {{tableName}} WHERE parent_id=?
        """ ), Map.of( "tableName", this.tableName ) );
        try ( PreparedStatement stmt = conn.prepareStatement(sql) ) {
            stmt.setLong(1, parentID);
            stmt.executeUpdate();
            return stmt.getUpdateCount();
        }
        catch (SQLException e) { throw new RuntimeException(e); }
    }



    public long deleteOrphans( int tooYoungAgeToDieSeconds ) {
        long tooYoungMicros = Lib.currentTimeMicros() - tooYoungAgeToDieSeconds * 1000L * 1000L;
        String sql = Lib.evalTemplate( Lib.unindent( """
            DELETE FROM {{tableName}} WHERE last_change_time < ?
            AND ? NOT IN (id,parent_id)
            AND parent_id NOT IN ( SELECT id FROM {{tableName}} )
        """ ), Map.of( "tableName", this.tableName ) );
        long totalRowCount = 0;
        while (true) {
            try ( PreparedStatement stmt = conn.prepareStatement(sql) ) {
                stmt.setLong(1,tooYoungMicros);
                stmt.setLong(2,R00T_ID);
                stmt.executeUpdate();
                long rowCount = stmt.getUpdateCount();
                if (rowCount<=0) break;
                totalRowCount += rowCount;
            } catch (SQLException e) { throw new RuntimeException(e); }
        }
        return totalRowCount;
    }



    /**
     * NOTE: decides whether to move rows up or down based on which is smaller row count
    **/
    private long createGap( long parentID, long insertBeforeEntryOrder, long gapSize ) {
        long[] minMaxEntryOrder = getMinMaxEntryOrder(parentID);
        if (minMaxEntryOrder==null) return 0L;
        long endRowCount = 1 + minMaxEntryOrder[1] - insertBeforeEntryOrder;
        long topRowCount = insertBeforeEntryOrder - minMaxEntryOrder[0];
        if (endRowCount<=0 || topRowCount<=0) return 0L; // NOTE: gap is before or after all rows
        long distance = gapSize;
        long movedCount;
        if ( topRowCount < endRowCount ) { // move top rows up (i.e. negative distance)
            long minEntryOrder = minMaxEntryOrder[0];
            long maxEntryOrder = insertBeforeEntryOrder - 1;
            distance = -1 * distance;
            movedCount = moveRows(parentID,minEntryOrder,maxEntryOrder,distance);
            Lib.asrtEQ(movedCount,topRowCount);
        } else { // move bottom rows down (i.e. positive distance)
            long minEntryOrder = insertBeforeEntryOrder;
            long maxEntryOrder = minMaxEntryOrder[1];
            movedCount = moveRows(parentID,minEntryOrder,maxEntryOrder,distance);
            Lib.asrtEQ(movedCount,endRowCount);
        }
        return movedCount;
    }



    private long removeGap(
        long parentID, long firstGapEntryOrder, long lastGapEntryOrder
    ) {
        long[] minMaxEntryOrder = getMinMaxEntryOrder(parentID);
        if (minMaxEntryOrder==null) return 0L;
        long topRowCount = firstGapEntryOrder - minMaxEntryOrder[0];
        long endRowCount = minMaxEntryOrder[1] - lastGapEntryOrder;
        if (endRowCount<=0 || topRowCount<=0) return 0L; // NOTE: gap is before or after all rows
        long distance = 1 + lastGapEntryOrder - firstGapEntryOrder;
        long movedCount;
        if ( topRowCount < endRowCount ) { // move top rows down (i.e. positive distance)
            long minEntryOrder = minMaxEntryOrder[0];
            long maxEntryOrder = minEntryOrder + topRowCount - 1;
            movedCount = moveRows(parentID,minEntryOrder,maxEntryOrder,distance);
            Lib.asrtEQ(movedCount,topRowCount);
        } else { // move bottom rows up (i.e. negative distance)
            distance = -1 * distance;
            long minEntryOrder = lastGapEntryOrder+1;
            long maxEntryOrder = minMaxEntryOrder[1];
            movedCount = moveRows(parentID,minEntryOrder,maxEntryOrder,distance);
            Lib.asrtEQ(movedCount,endRowCount);
        }
        return movedCount;
    }



    private long moveRows( long parentID, long minEntryOrder, long maxEntryOrder, long distance ) {
        String sql = Lib.evalTemplate( Lib.unindent( """
            UPDATE {{tableName}}
            SET entry_order = entry_order + ?
            WHERE parent_id=? AND entry_order >= ? AND entry_order <= ?
        """ ), Map.of( "tableName", this.tableName ) );
        try ( PreparedStatement stmt = conn.prepareStatement(sql) ) {
            stmt.setLong(1, distance);
            stmt.setLong(2, parentID);
            stmt.setLong(3, minEntryOrder);
            stmt.setLong(4, maxEntryOrder);
            long rowCount = stmt.executeUpdate();
            return rowCount;
        }
        catch (SQLException e) { throw new RuntimeException(e); }
    }



    @SuppressWarnings("unused")
    private static boolean gap_TEST_( boolean findLineNumber ) throws Exception {
        if (findLineNumber) throw new RuntimeException();
        List< Map<String,Object> > actualData = null;
        try ( PersistentData pd = PersistentData.temp() ) {
            {
                long id = Lib.currentTimeMicros();
                pd.row( id, R00T_ID, 0L, "0", "0", id ).insert();
                id = Lib.currentTimeMicros();
                pd.row( id, R00T_ID, 1L, "1", "1", id ).insert();
                id = Lib.currentTimeMicros();
                pd.row( id, R00T_ID, 2L, "2", "2", id ).insert();
                id = Lib.currentTimeMicros();
                pd.row( id, R00T_ID, 3L, "3", "3", id ).insert();
                id = Lib.currentTimeMicros();
                pd.row( id, R00T_ID, 4L, "4", "4", id ).insert();
            }
            { // create a gap near the bottom
                pd.createGap( R00T_ID, 3L, 2L );
                long[] minMaxEntryOrder = pd.getMinMaxEntryOrder(R00T_ID);
                Lib.asrtEQ( minMaxEntryOrder[1], 6L );
                actualData = pd.debugDump();
                Object actual = Jsonable.get( actualData, List.of(3,"entryOrder") );
                Lib.asrtEQ(actual,5);
                actual = Jsonable.get( actualData, List.of(2,"entryOrder") );
                Lib.asrtEQ(actual,2);
            }
            { // remove that gap
                pd.removeGap( R00T_ID, 3L, 4L );
                Lib.asrt( pd.entryOrderCheck(R00T_ID,true) );
            }
            { // create a gap near the top
                pd.createGap( R00T_ID, 2L, 2L );
                actualData = pd.debugDump();
                Object actual = Jsonable.get( actualData, List.of(0,"entryOrder") );
                Lib.asrtEQ(actual,-2);
                actual = Jsonable.get( actualData, List.of(1,"entryOrder") );
                Lib.asrtEQ(actual,-1);
                actual = Jsonable.get( actualData, List.of(2,"entryOrder") );
                Lib.asrtEQ(actual,2);
            }
            { // remove that gap
                pd.removeGap( R00T_ID, 0L, 1L );
                Lib.asrt( pd.entryOrderCheck(R00T_ID,true) );
                long[] minMaxEntryOrder = pd.getMinMaxEntryOrder(R00T_ID);
                Lib.asrtEQ( minMaxEntryOrder[0], 0L );
                Lib.asrtEQ( minMaxEntryOrder[1], 4L );
            }
        }
        return true;
    }



    @Override
    public void close() throws Exception {
        while (! beforeClose.isEmpty()) {
            try{ beforeClose.removeFirst().run(); }catch(Throwable ignore){ Lib.log(ignore); }
        }
        try {
            if (!conn.isClosed()) {
                if (conn.getMetaData().getURL().startsWith("jdbc:hsqldb:file:")) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("SHUTDOWN");
                    }
                }
                conn.close();
            }
        }catch(Throwable ignore){ Lib.log(ignore); }
        while (! afterClose.isEmpty()) {
            try{ afterClose.removeFirst().run(); }catch(Throwable ignore){ Lib.log(ignore); }
        }
    }



    public List< Map<String,Object> > debugDump() {
        ArrayList<Long> ids = new ArrayList<>();
        try (
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery( "SELECT id FROM "+tableName+" ORDER BY parent_id,entry_order" )
        ) {
            while ( rs.next() ) ids.add( rs.getLong("id") );
        }
        catch (SQLException e) { throw new RuntimeException(e); }
        ArrayList< Map<String,Object> > rows = new ArrayList<>();
        for ( long id : ids ) {
            Row row = row(id).select();
            rows.add( row.toMap() );
        }
        return Collections.unmodifiableList(rows);
    }



    public boolean entryOrderCheck( long parentID, boolean exhaustive ) {
        long[] minMaxEntryOrder = getMinMaxEntryOrder(parentID);
        if (minMaxEntryOrder==null) return true;
        long expectedRowCount = 1 + minMaxEntryOrder[1] - minMaxEntryOrder[0];
        try ( PreparedStatement stmt = conn.prepareStatement(
            "SELECT COUNT(*) AS count FROM "+tableName+" WHERE parent_id=?"
        ) ) {
            stmt.setLong(1, parentID);
            try ( ResultSet rs = stmt.executeQuery() ) {
                if (! rs.next() ) return false;
                long rowCount = rs.getLong("count");
                if (rowCount!=expectedRowCount) return false;
            }
        }
        catch (SQLException e) { throw new RuntimeException(e); }
        if (!exhaustive) return true;
        { // now we step through every row to guarantee entry order is consecutive
            long expectedEntryOrder = minMaxEntryOrder[0];
            for ( Row row : rowIterable(parentID) ) {
                if (row.entryOrder!=expectedEntryOrder) return false;
                expectedEntryOrder++;
            }
        }
        return true;
    }



    public static void main( String[] args ) { Lib.testClass(); }
}
