package server;

import com.google.gson.*;

import java.io.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MyDatabase {
    private JsonObject db;
    private Gson gson;

    private static final String DB_FILENAME = System.getProperty("user.dir") + File.separator +
//            "JSON Database with Java" + File.separator +
//            "task" + File.separator +
            "src" + File.separator +
            "server" + File.separator +
            "data" + File.separator + "db.json";
    private static ReadWriteLock lock;

    public MyDatabase() {
        gson = new GsonBuilder().setPrettyPrinting().create();
        lock = new ReentrantReadWriteLock();
        loadDB();
    }

    private void loadDB()  {
        File dbFile = new File(DB_FILENAME);
        try {
            if (!dbFile.exists()) {
                dbFile.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (FileReader reader = new FileReader(DB_FILENAME)){
            db = gson.fromJson(reader, JsonObject.class);
            if (db == null)
                db = new JsonObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void safeDB() {
        File dbFile = new File(DB_FILENAME);
        try (FileWriter writer = new FileWriter(dbFile, false)){
            gson.toJson(db, writer);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JsonElement getValueWithKeyArray(JsonObject dbObject, JsonArray keyArray) {
        // check for empty keyArray
        if (keyArray.size() == 0) {
            return null;
        }

        // get and lookup first key in array
        String key = keyArray.get(0).getAsString();

        if (dbObject.has(key)) {
            JsonElement value = dbObject.get(key);

            // if there are more keys AND value is another jsonObject: remove first key and find next value recursively
            //  (using value as subset of db recursively)
            if ((keyArray.size() > 1) && value.isJsonObject()) {
                keyArray.remove(0);
                return getValueWithKeyArray(value.getAsJsonObject(), keyArray);
            }
            else {
                // only return value if keyArray was completely exhausted
                if (keyArray.size() == 1) {
                    return value;
                }
                else {
                    // wrong keys in array
                    return null;
                }
            }
        }
        else {
            return null;
        }
    }

    private JsonObject getObject(JsonElement key) {
        JsonObject response = new JsonObject();
        lock.readLock().lock();
        try {
            if (key.isJsonPrimitive()) {
                if (db.has(key.getAsString())) {
                    response.addProperty("response", "OK");
                    response.add("value", db.get(key.getAsString()));
                    return response;
                }
            }
            else if (key.isJsonArray()){
                JsonElement value = getValueWithKeyArray(db, key.getAsJsonArray());
                if (value != null) {
                    response.addProperty("response", "OK");
                    response.add("value", value);
                    return response;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            lock.readLock().unlock();
        }
        response.addProperty("response", "ERROR");
        response.addProperty("reason", "no such key");
        return response;
    }

    private void addValueWithKeyArray (JsonObject dbObject, JsonArray keyArray, JsonElement value) {
        if (keyArray.size() == 0) {
            return;
        }

        String key = keyArray.get(0).getAsString();

        if (dbObject.has(key)) {
            // key already existing:
            // if more than 1 key left in array, recursively move through dbObjects to add value at right key-level
            if (keyArray.size() > 1) {
                // array size > 1 means value must be an object
                JsonElement subElement = dbObject.get(key);
                if (subElement.isJsonObject()) {
                    // value for this key is already an object, recursively go further into the tree to add value
                    JsonObject subObject = subElement.getAsJsonObject();
                    keyArray.remove(0);
                    addValueWithKeyArray(subObject, keyArray, value);
                }
                else {
                    // value is not an object, create new one and add value (via recursive call which will go
                    // straight to "key does not exist" below)
                    JsonObject subObject = new JsonObject();
                    keyArray.remove(0);
                    addValueWithKeyArray(subObject, keyArray, value);
                    // add new subObject into higher-level object
                    dbObject.add(key, subObject);
                }
            }
            else {
                // no more keys, set value at current dbobject-level
                // (add will either replace current value or add new key/value if get(key) would be a jsonobject
                dbObject.add(key, value);
            }
        }
        else {
            // key doesn't exit, create new key(s) and add value
            while (keyArray.size() > 1) {
                // first we create the tree of all sub-keys, if any
                JsonObject subObject = new JsonObject();
                String nextKey = keyArray.remove(0).getAsString();
                dbObject.add(nextKey, subObject);
                dbObject = subObject;
            }
            String finalKey = keyArray.get(0).getAsString();
            // dbObject already set to the correct level in tree-structure in while loop if we had to create keys
            dbObject.add(finalKey, value);
        }
    }

    private JsonObject setObject(JsonElement key, JsonElement value) {
        JsonObject response = new JsonObject();
        response.addProperty("response", "OK");
        lock.writeLock().lock();
        try {
            if (key.isJsonPrimitive()) {
                db.add(key.getAsString(), value);
                safeDB();
            }
            else {
                if (key.isJsonArray()) {
                    addValueWithKeyArray(db, key.getAsJsonArray(), value);
                    safeDB();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            lock.writeLock().unlock();
        }
        return response;
    }

    private boolean removeValueWithKeyArray (JsonObject dbObject, JsonArray keyArray) {
        boolean response = false;
        if (keyArray.size() == 0) {
            return false;
        }

        String key = keyArray.get(0).getAsString();

        if (dbObject.has(key)) {
            if (keyArray.size() > 1) {
                // more keys available
                JsonElement subElement = dbObject.get(key);
                if (subElement.isJsonObject()) {
                    // and subElement is an Object, recursively run through removeValue... one level down
                    JsonObject subObject = subElement.getAsJsonObject();
                    keyArray.remove(0);
                    return removeValueWithKeyArray(subObject, keyArray);
                }
                else {
                    // sequence in keyArray not found (we have keys left but element is not an object anymore)
                    return false;
                }
            }
            else {
                // keyArray == 1 here, remove key/value
                return dbObject.remove(key) != null;  // could also just remove and return true (tbc)
            }
        }
        else
            // key not found in current dbObject
            return false;
    }

    private JsonObject deleteObject(JsonElement key)  {
        JsonObject response = new JsonObject();
        lock.writeLock().lock();
        try {
            if (key.isJsonPrimitive()) {
                if (db.remove(key.getAsString()) != null) {
                    safeDB();
                    response.addProperty("response", "OK");
                    return response;
                }
            }
            else {
                if (key.isJsonArray()) {
                    if (removeValueWithKeyArray(db, key.getAsJsonArray())) {
                        safeDB();
                        response.addProperty("response", "OK");
                        return response;
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            lock.writeLock().unlock();
        }
        response.addProperty("response", "ERROR");
        response.addProperty("value", "no such key");
        return response;
    }

    public String msgHandler(JsonObject msg) {
        JsonObject response = null;

        switch (msg.get("type").getAsString()) {
            case "set":
                response = setObject(msg.get("key"), msg.get("value"));
                break;
            case "get":
                response = getObject(msg.get("key"));
                break;
            case "delete":
                response = deleteObject(msg.get("key"));
                break;
        }
        return new Gson().toJson(response);
    }

}
