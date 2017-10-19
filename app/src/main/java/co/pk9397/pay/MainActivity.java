package co.pk9397.pay;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity {

    private AsyncHttpServer server = new AsyncHttpServer();
    private AsyncServer mAsyncServer = new AsyncServer();

    TextView balance;
    Spinner dropdown;
    Button pay, bc;

    SharedPreferences pref;
    JSONObject nodes = new JSONObject();
    JSONObject nums = new JSONObject();

    int count;
    double bal;
    int Limit = 7;
    boolean consensus_bool = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = getSharedPreferences("settings", 0);

        // Number
        if(pref.getString("num", "-").equals("-")) {
            setContentView(R.layout.front_page);
            Button save = (Button)findViewById(R.id.button);

            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EditText num = (EditText)findViewById(R.id.editText);
                    String num_s = num.getText().toString();
                    if(!num_s.isEmpty()) {
                        pref.edit().putString("num", num_s).apply();
                        Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                        recreate();
                    }
                }
            });
        }
        else {
            setContentView(R.layout.activity_main);
            Toast.makeText(this, "Hi " + pref.getString("num", "client") + "!", Toast.LENGTH_LONG).show();

            dropdown = (Spinner)findViewById(R.id.spinner1);
            balance = (TextView) findViewById(R.id.textView);
            pay = (Button) findViewById(R.id.button3);
            bc = (Button) findViewById(R.id.button2);

            // Genesis Block
            if (pref.getString("blockchain", "-").equals("-")) {
                JSONArray blockchain = new JSONArray();
                JSONObject block = new JSONObject();
                try {
                    JSONObject transaction = new JSONObject();
                    transaction.put("sender", "0");
                    transaction.put("receiver", pref.getString("num", ""));
                    transaction.put("amount", 15);
                    block.put("index", 1);
                    block.put("time", System.currentTimeMillis() / 1000);
                    block.put("transactions", new JSONArray().put(transaction));
                    block.put("nounce", 0);
                    block.put("prehash", "0");
                    blockchain.put(block);
                    pref.edit().putString("blockchain", blockchain.toString()).apply();
                    pref.edit().putString("transactions", new JSONArray().toString()).apply();
                    Log.i("MainActivity", blockchain.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

            // Default Difficulty
            if (pref.getInt("difficulty", 0) == 0) {
                pref.edit().putInt("difficulty", 3).apply();
            }

            bal = 0;
            try {
                bal = get_balance();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            balance.setText("Balance: " + bal + "");

            consensus();

            pay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(consensus_bool) {
                        String to = dropdown.getSelectedItem().toString();
                        EditText amount = (EditText) findViewById(R.id.editText5);
                        double amt;
                        if(!amount.getText().toString().isEmpty()) {
                            amt = Double.parseDouble(amount.getText().toString());
                            if (to != null && amt > 0 && amt < bal) {
                                Toast.makeText(MainActivity.this, "You Can Pay", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Give Valid Input", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                    else {
                        Toast.makeText(MainActivity.this, "Please Wait", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
        bc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, BlockchainActivity.class);
                startActivity(i);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        server.stop();
        mAsyncServer.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        startServer();
    }

    private void startServer() {
        server.get("/num", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                try {
                    response.code(200);
                    response.send(new JSONObject().accumulate("num", pref.getString("num", "")));
                } catch (JSONException e) {
                    Log.e("MainActivity", e.getMessage(), e);
                }
            }
        });
        server.get("/blockchain", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                try {
                    response.code(200);
                    JSONObject res = new JSONObject();
                    JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
                    res.put("blockchain", blockchain);
                    res.put("length", blockchain.length());
                    response.send(res);
                } catch (JSONException e) {
                    Log.e("MainActivity", e.getMessage(), e);
                }
            }
        });
        server.listen(mAsyncServer, 8080);
    }

    private String hash(String text) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.update(text.getBytes());
            byte[] digest = md.digest();
            return Base64.encodeToString(digest, Base64.DEFAULT);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        int difficulty = pref.getInt("difficulty", 3);
        return String.format("%0" + difficulty + "d", 0);
    }

    private String last_block() throws JSONException {
        JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
        return blockchain.getJSONObject(blockchain.length()-1).toString();
    }

    private void mine() throws JSONException {

        //Validate
        JSONArray unsurechain = new JSONArray(pref.getString("blockchain", ""));
        unsurechain.put(new JSONObject().put("transactions", pref.getString("transactions", "")));

        if(validate_transactions(unsurechain)) {

            String lb = last_block();
            long nounce = 0;
            int difficulty = pref.getInt("difficulty", 3);
            // Proof of Work
            while (!hash(lb + String.valueOf(nounce)).substring(0, difficulty).equals(String.format("%0" + difficulty + "d", 0))) {
                nounce++;
            }
            // Get Reward
            JSONArray transactions = new JSONArray(pref.getString("transactions", ""));
            JSONObject transaction = new JSONObject();
            transaction.put("sender", "0");
            transaction.put("receiver", pref.getString("num", ""));
            transaction.put("amount", 10);
            transactions.put(transaction);

            // Create New Block
            JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
            JSONObject block = new JSONObject();
            block.put("index", blockchain.length() + 1);
            block.put("time", System.currentTimeMillis() / 1000);
            block.put("transactions", transactions);
            block.put("nounce", nounce);
            block.put("prehash", hash(lb));
            blockchain.put(block);

            // Save Blockchain and Clear Transactions
            pref.edit().putString("transactions", new JSONArray().toString()).apply();
            pref.edit().putString("blockchain", blockchain.toString()).apply();
        }
        else {
            // Get from others
            // We can revert to a valid transaction - may be
        }
    }

    private boolean validate_chain(JSONArray blockchain) throws JSONException {
        JSONObject curr, prev = blockchain.getJSONObject(0);
        int difficulty = pref.getInt("difficulty", 3);
        for(int i=1; i<blockchain.length(); ++i) {
            curr = blockchain.getJSONObject(i);
            if(!curr.getString("prehash").equals(hash(prev.toString()))) {
                return false;
            }
            if(!hash(prev + String.valueOf(curr.getInt("nounce"))).substring(0, difficulty).equals(String.format("%0" + difficulty + "d", 0))){
                return false;
            }
        }
        return true;
    }

    private boolean validate_transactions(JSONArray blockchain) throws JSONException {
        JSONObject ledger = new JSONObject();
        for(int i=0; i<blockchain.length(); ++i) {
            JSONArray transactions = blockchain.getJSONObject(i).getJSONArray("transactions");
            for(int j=0; j<transactions.length(); ++j) {
                JSONObject transaction = transactions.getJSONObject(j);
                String receiver = transaction.getString("receiver");
                String sender = transaction.getString("sender");
                double amount = transaction.getDouble("amount");
                double prev_amount;
                if(!sender.equals("0")) {
                    prev_amount = 0;
                    if(ledger.has(sender)) {
                        prev_amount = ledger.getDouble(sender);
                    }
                    if(prev_amount - amount < 0) {
                        return false;
                    }
                    ledger.put(sender, prev_amount - amount);
                }
                prev_amount = 0;
                if(ledger.has(receiver)) {
                    prev_amount = ledger.getDouble(receiver);
                }
                ledger.put(receiver, prev_amount + amount);
            }
        }
        return true;
    }

    private double get_balance() throws JSONException {
        JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
        JSONObject ledger = new JSONObject();
        for(int i=0; i<blockchain.length(); ++i) {
            JSONArray transactions = blockchain.getJSONObject(i).getJSONArray("transactions");
            for(int j=0; j<transactions.length(); ++j) {
                JSONObject transaction = transactions.getJSONObject(j);
                String receiver = transaction.getString("receiver");
                String sender = transaction.getString("sender");
                double amount = transaction.getDouble("amount");
                double prev_amount;
                if(!sender.equals("0")) {
                    prev_amount = 0;
                    if(ledger.has(sender)) {
                        prev_amount = ledger.getDouble(sender);
                    }
                    ledger.put(sender, prev_amount - amount);
                }
                prev_amount = 0;
                if(ledger.has(receiver)) {
                    prev_amount = ledger.getDouble(receiver);
                }
                ledger.put(receiver, prev_amount + amount);
            }
        }
        double amount = 0;
        if(ledger.has(pref.getString("num", ""))) {
            amount = ledger.getDouble(pref.getString("num", ""));
            Log.i("Balance From", "" + amount + "");
        }
        Log.i("Balance", "" + amount + "");
        return amount;
    }

    private class get extends AsyncTask<String , Void, String> {
        String server_response;

        @Override
        protected String doInBackground(String ... strings) {
            URL url;
            HttpURLConnection urlConnection = null;

            try {
                count++;
                url = new URL(strings[0]);
                Log.i("URL", url.toString());
                urlConnection = (HttpURLConnection) url.openConnection();

                int responseCode = urlConnection.getResponseCode();

                if(responseCode == HttpURLConnection.HTTP_OK){
                    server_response = readStream(urlConnection.getInputStream());
                    Log.v("CatalogClient", server_response);
                    if(url.toString().contains("num")) {
                        JSONObject response = new JSONObject(server_response);
                        nodes.put(url.toString().substring(0, url.toString().length()-4), response.getString("num"));
                        nums.put(response.getString("num"), url.toString().substring(0, url.toString().length()-4));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if(count == Limit && nodes.names() != null) {
                String[] items = new String[nodes.names().length()];
                for(int i = 0; i<nodes.names().length(); i++){
                    try {
                        String host = nodes.names().getString(i);
                        items[i] = nodes.getString(host);
                        new get().execute(host + "/blockchain");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                dropdown.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, items));
            }
            else if(count > Limit) {
                try {
                    Log.i("BlockchainConflict", "" + server_response + "");
                    JSONObject res = new JSONObject(server_response);
                    JSONArray nblockchain = res.getJSONArray("blockchain");
                    int nlength = res.getInt("length");
                    JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
                    if(nlength > blockchain.length() && validate_chain(nblockchain)) {
                        pref.edit().putString("blockchain", nblockchain.toString()).apply();
                        Toast.makeText(MainActivity.this, "Blockchain Updated", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            int num_nodes = 0;
            if(nodes.names() != null) {
                num_nodes = nodes.names().length();
            }
            if(count == Limit + num_nodes) {
                consensus_bool = true;
            }
        }
    }

// Converting InputStream to String

    private String readStream(InputStream in) {
        BufferedReader reader = null;
        StringBuffer response = new StringBuffer();
        try {
            reader = new BufferedReader(new InputStreamReader(in));
            String line = "";
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return response.toString();
    }

    private void consensus() {
        // Get all neighbouring nodes and find longest valid chain and change to it
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        String myip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        StringBuilder subnet = new StringBuilder(myip);
        for(int i = subnet.length()-1; subnet.charAt(i) != '.'; --i) {
            subnet.deleteCharAt(i);
        }
        count = 1;
        for (int i=1; i<Limit; i++){
            String host = "http://" + subnet + i + ":8080";
            new get().execute(host + "/num");
        }
    }

}
