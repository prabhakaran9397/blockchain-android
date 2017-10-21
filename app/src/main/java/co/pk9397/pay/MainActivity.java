package co.pk9397.pay;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.text.method.ScrollingMovementMethod;
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
import java.net.InetAddress;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private AsyncHttpServer server = new AsyncHttpServer();
    private AsyncServer mAsyncServer = new AsyncServer();

    TextView balance, trans, progperc;
    Spinner dropdown;
    Button pay, bc, con;

    SharedPreferences pref;

    boolean consensus_bool = false;

    Set<String> nums = new HashSet<String>();
    Set<String> nodes = new HashSet<String>();
    JSONObject current_transaction = new JSONObject();

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
            trans = (TextView) findViewById(R.id.textView3);
            progperc = (TextView) findViewById(R.id.textView4);
            trans.setMovementMethod(new ScrollingMovementMethod());
            pay = (Button) findViewById(R.id.button3);
            bc = (Button) findViewById(R.id.button2);
            con = (Button) findViewById(R.id.button4);

            // Genesis Block
            if (pref.getString("blockchain", "-").equals("-")) {
                JSONArray blockchain = new JSONArray();
                JSONObject block = new JSONObject();
                try {
                    JSONObject transaction = new JSONObject();
                    transaction.put("sender", "0");
                    transaction.put("receiver", "7200210789");
                    transaction.put("amount", 15);
                    block.put("index", 1);
                    block.put("time", System.currentTimeMillis() / 1000);
                    block.put("transactions", new JSONArray().put(transaction));
                    block.put("nounce", 0);
                    block.put("prehash", "0");
                    blockchain.put(block);
                    pref.edit().putString("blockchain", blockchain.toString()).apply();
                    JSONArray transactions = new JSONArray();
                    transactions.put(transaction);
                    pref.edit().putString("transactions", transactions.toString()).apply();
                    Log.i("MainActivity", blockchain.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

            // Default Difficulty
            if (pref.getInt("difficulty", 0) == 0) {
                pref.edit().putInt("difficulty", 2).apply();
            }

            update_balance();
            balance.setText("Balance: " + pref.getFloat("balance", 0) + "");
            trans.setText("" + formatString(pref.getString("transactions", "{}")) + "");

            pay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(consensus_bool) {
                        String to = dropdown.getSelectedItem().toString();
                        EditText amount = (EditText) findViewById(R.id.editText5);
                        double amt;
                        if(!amount.getText().toString().isEmpty()) {
                            amt = Double.parseDouble(amount.getText().toString());
                            double bal = pref.getFloat("balance", 0);
                            if (to != null && amt > 0 && amt < bal) {
                                // Make Transaction
                                JSONObject t = new JSONObject();
                                try {
                                    t.put("sender", pref.getString("num", ""));
                                    t.put("receiver", to);
                                    t.put("amount", amt);
                                    new_transaction(t);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                amount.setText("");
                                Toast.makeText(MainActivity.this, "Transaction Queued!", Toast.LENGTH_LONG).show();
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
            bc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(MainActivity.this, BlockchainActivity.class);
                    startActivity(i);
                }
            });
            con.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new consensus().execute("");
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        server.stop();
        mAsyncServer.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        startServer();
    }

    private void startServer() {
        server.get("/blockchain", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                try {
                    response.code(200);
                    JSONObject res = new JSONObject();
                    JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
                    JSONArray transactions = new JSONArray(pref.getString("transactions", ""));
                    res.put("num",pref.getString("num", "0"));
                    res.put("blockchain", blockchain);
                    res.put("length", blockchain.length());
                    res.put("transactions", transactions);
                    response.send(res);
                } catch (JSONException e) {
                    Log.e("MainActivity", e.getMessage(), e);
                }
            }
        });
        server.listen(mAsyncServer, 8080);
    }

    private void new_transaction(JSONObject transaction) {
        try {
            JSONArray transactions = new JSONArray(pref.getString("transactions", ""));
            transactions.put(transaction);
            pref.edit().putString("transactions", transactions.toString()).apply();
            current_transaction = transaction;
            new consensus().execute("mine");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String hash(String text) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA1");
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

    private void update_balance() {
        try {
            JSONArray transactions = new JSONArray(pref.getString("transactions", ""));
            Log.e("BALANCE TRANS", transactions.toString());
            JSONObject ledger = new JSONObject();
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
            double amount = 0;
            if(ledger.has(pref.getString("num", ""))) {
                amount = ledger.getDouble(pref.getString("num", ""));
                Log.i("Balance From", "" + amount + "");
            }
            Log.i("Balance", "" + amount + "");
            pref.edit().putFloat("balance", Float.parseFloat(String.valueOf(amount))).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class consensus extends AsyncTask<String, String, String> {
        String server_response;
        @Override
        protected String doInBackground(String ... strings) {
            try
            {
                if(strings[0].equals("mine")) {

                    for (String fullpath : nodes) {
                        try {
                            Log.e("PING", fullpath);
                            URL url = new URL(fullpath);
                            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                            int responseCode = urlConnection.getResponseCode();

                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                server_response = readStream(urlConnection.getInputStream());
                                Log.v("CURL", server_response);

                                JSONObject res = new JSONObject(server_response);
                                JSONArray nblockchain = res.getJSONArray("blockchain");
                                JSONArray ntransactions = res.getJSONArray("transactions");
                                int nlength = res.getInt("length");
                                nums.add(res.getString("num"));
                                nodes.add(fullpath);
                                JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
                                if (nlength > blockchain.length() && validate_chain(nblockchain)) {
                                    pref.edit().putString("blockchain", nblockchain.toString()).apply();
                                    pref.edit().putString("transactions", ntransactions.toString()).apply();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    String lb = last_block();
                    long nounce = 0;
                    int difficulty = pref.getInt("difficulty", 3);
                    // Proof of Work
                    while (!hash(lb + String.valueOf(nounce)).substring(0, difficulty).equals(String.format("%0" + difficulty + "d", 0))) {
                        nounce++;
                    }
                    // Get Reward
                    JSONArray transactions = new JSONArray();
                    transactions.put(current_transaction);
                    JSONObject transaction = new JSONObject();
                    transaction.put("sender", "0");
                    transaction.put("receiver", pref.getString("num", ""));
                    transaction.put("amount", 0.1);
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

                    // Save Blockchain
                    pref.edit().putString("blockchain", blockchain.toString()).apply();
                    current_transaction = null;
                }
                else {
                    WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
                    String myip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
                    InetAddress host;
                    Log.e("PING", "STARTED");
                    host = InetAddress.getByName(String.valueOf(myip));
                    byte[] ip = host.getAddress();

                    for (int i = 1; i < 255; i++) {
                        try {
                            ip[3] = (byte) i;
                            InetAddress address = InetAddress.getByAddress(ip);
                            publishProgress(address.toString().substring(1, address.toString().length()));
                            if (!address.toString().equals("/" + myip) && address.isReachable(100)) {
                                String fullpath = "http:/" + address.toString() + ":8080/blockchain";
                                Log.e("PING", fullpath);
                                URL url = new URL(fullpath);
                                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                                int responseCode = urlConnection.getResponseCode();

                                if (responseCode == HttpURLConnection.HTTP_OK) {
                                    server_response = readStream(urlConnection.getInputStream());
                                    Log.v("CURL", server_response);

                                    JSONObject res = new JSONObject(server_response);
                                    JSONArray nblockchain = res.getJSONArray("blockchain");
                                    JSONArray ntransactions = res.getJSONArray("transactions");
                                    int nlength = res.getInt("length");
                                    nums.add(res.getString("num"));
                                    nodes.add(fullpath);
                                    JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
                                    if (nlength > blockchain.length() && validate_chain(nblockchain)) {
                                        pref.edit().putString("blockchain", nblockchain.toString()).apply();
                                        pref.edit().putString("transactions", ntransactions.toString()).apply();
                                    }
                                }
                            }
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                    Log.e("PING", "ENDED");
                }
            } catch(Exception e1) {
                e1.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String ... values) {
            super.onProgressUpdate(values);
            progperc.setText("scanning " + values[0] + "");
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            consensus_bool = false;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            dropdown.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, nums.toArray()));
            consensus_bool = true;
            progperc.setText("");
            update_balance();
            balance.setText("Balance: " + pref.getFloat("balance", 0) + "");
            trans.setText("" + formatString(pref.getString("transactions", "{}")) + "");
            Toast.makeText(MainActivity.this, "Consensus Met", Toast.LENGTH_LONG).show();
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

    public static String formatString(String text){

        StringBuilder json = new StringBuilder();
        String indentString = "";

        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            switch (letter) {
                case '{':
                case '[':
                    json.append("\n").append(indentString).append(letter).append("\n");
                    indentString = indentString + "\t";
                    json.append(indentString);
                    break;
                case '}':
                case ']':
                    indentString = indentString.replaceFirst("\t", "");
                    json.append("\n").append(indentString).append(letter);
                    break;
                case ',':
                    json.append(letter).append("\n").append(indentString);
                    break;

                default:
                    json.append(letter);
                    break;
            }
        }

        return json.toString();
    }

}
