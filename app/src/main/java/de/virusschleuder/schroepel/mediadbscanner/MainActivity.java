package de.virusschleuder.schroepel.mediadbscanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.AsyncTask;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.WebView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends AppCompatActivity implements OnClickListener {
    private Button scanBtn, sendBtn, previewBtn, linkBtn;
    private TextView authorText, titleText, descriptionText, dateText, ratingCountText;
    private LinearLayout starLayout;
    private ImageView thumbView;
    private ImageView[] starViews;
    private Bitmap thumbImg;
    private String isbnCode = "", errorCode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanBtn = (Button)findViewById(R.id.scan_button);
        scanBtn.setOnClickListener(this);

        sendBtn = (Button)findViewById(R.id.send_button);
        sendBtn.setOnClickListener(this);
        sendBtn.setVisibility(View.GONE);

        previewBtn = (Button)findViewById(R.id.preview_btn);
        previewBtn.setVisibility(View.GONE);
        previewBtn.setOnClickListener(this);

        linkBtn = (Button)findViewById(R.id.link_btn);
        linkBtn.setVisibility(View.GONE);
        linkBtn.setOnClickListener(this);

        authorText = (TextView)findViewById(R.id.book_author);
        titleText = (TextView)findViewById(R.id.book_title);
        descriptionText = (TextView)findViewById(R.id.book_description);
        dateText = (TextView)findViewById(R.id.book_date);
        starLayout = (LinearLayout)findViewById(R.id.star_layout);
        ratingCountText = (TextView)findViewById(R.id.book_rating_count);
        thumbView = (ImageView)findViewById(R.id.thumb);

        starViews = new ImageView[5];
        for (int s=0; s<starViews.length; s++) {
            starViews[s] = new ImageView(this);
        }

        if (savedInstanceState != null) {
            authorText.setText(savedInstanceState.getString("author"));
            titleText.setText(savedInstanceState.getString("title"));
            descriptionText.setText(savedInstanceState.getString("description"));
            dateText.setText(savedInstanceState.getString("date"));
            ratingCountText.setText(savedInstanceState.getString("ratings"));
            int numStars = savedInstanceState.getInt("stars"); // zero if null
            for (int s=0; s<numStars; s++) {
                starViews[s].setImageResource(R.drawable.star);
                starLayout.addView(starViews[s]);
            }
            starLayout.setTag(numStars);
            thumbImg = (Bitmap)savedInstanceState.getParcelable("thumbPic");
            thumbView.setImageBitmap(thumbImg);
            previewBtn.setTag(savedInstanceState.getString("isbn"));

            if (savedInstanceState.getBoolean("isEmbed"))
                previewBtn.setEnabled(true);
            else
                previewBtn.setEnabled(false);
            if (savedInstanceState.getInt("isLink") == View.VISIBLE)
                linkBtn.setVisibility(View.VISIBLE);
            else
                linkBtn.setVisibility(View.GONE);
            previewBtn.setVisibility(View.VISIBLE);
            if (savedInstanceState.getInt("isSend") == View.VISIBLE)
                sendBtn.setVisibility(View.VISIBLE);
            else
                sendBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.scan_button) {
            // scan
            isbnCode = "";
            IntentIntegrator scanIntegrator = new IntentIntegrator(this);
            scanIntegrator.setTitle("MediaDBScanner");
//            scanIntegrator.addExtra("SCAN_WIDTH", 800);
//            scanIntegrator.addExtra("SCAN_HEIGHT", 200);
//            scanIntegrator.addExtra("RESULT_DISPLAY_DURATION_MS", 3000L);
//            scanIntegrator.addExtra("PROMPT_MESSAGE", "Custom prompt to scan a product");
            sendBtn.setVisibility(View.GONE);
            scanIntegrator.initiateScan();
        } else if (view.getId() == R.id.link_btn) {
            // get the url tag
            String tag = (String) view.getTag();
            // launch the url
            Intent webIntent = new Intent(Intent.ACTION_VIEW);
            webIntent.setData(Uri.parse(tag));
            startActivity(webIntent);
        } else if (view.getId() == R.id.preview_btn) {
            String tag = (String)view.getTag();
            // launch preview
            Intent intent = new Intent(this, EmbeddedBook.class);
            intent.putExtra("isbn", tag);
            startActivity(intent);
        } else if (view.getId() == R.id.send_button) {
            if (isbnCode != "") {
//                String url = "https://bugzone.virusschleuder.de/mediadb/api.php/1/" + isbnCode;
                new sendISBN(MainActivity.this).execute(isbnCode);
            }
        }
    }

    protected void onSaveInstanceState(Bundle savedBundle) {
        savedBundle.putString("title", "" + titleText.getText());
        savedBundle.putString("author", "" + authorText.getText());
        savedBundle.putString("description", "" + descriptionText.getText());
        savedBundle.putString("date", "" + dateText.getText());
        savedBundle.putString("ratings", "" + ratingCountText.getText());
        savedBundle.putParcelable("thumbPic", thumbImg);
        if (starLayout.getTag() != null)
            savedBundle.putInt("stars", Integer.parseInt(starLayout.getTag().toString()));
        savedBundle.putBoolean("isEmbed", previewBtn.isEnabled());
        savedBundle.putInt("isLink", linkBtn.getVisibility());
        if (previewBtn.getTag() != null)
            savedBundle.putString("isbn", previewBtn.getTag().toString());
        savedBundle.putInt("isSend", sendBtn.getVisibility());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // retrieve scan result
        IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

        if (scanningResult != null) {
            // we have a result
            String scanContent = scanningResult.getContents();
            String scanFormat = scanningResult.getFormatName();
            Log.v("SCAN", "Content: " + scanContent + " - Format: " + scanFormat);

            if ((scanContent != null) && (scanFormat != null) && (scanFormat.equalsIgnoreCase("EAN_13"))) {
                // book search
                sendBtn.setVisibility(View.VISIBLE);
                isbnCode = scanContent;
                previewBtn.setTag(scanContent);
                String bookSearchString = "https://www.googleapis.com/books/v1/volumes?" +
                        "q=isbn:" + scanContent + "&key=AIzaSyDCo9K-uPwA2OpiZ18p4cOrm79If9RLbsY";
                new GetBookInfo(MainActivity.this).execute(bookSearchString);
            }
        } else {
            Toast toast = Toast.makeText(getApplicationContext(), "No scan data received!", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private class GetBookInfo extends AsyncTask<String, Void, String> {
        Context context;
        public ProgressDialog dialog;
        // fetch book info
        // fetch book info

        public GetBookInfo(Activity activity) {
            this.context = activity;
        }

        @Override
        protected String doInBackground(String... bookURLs) {
            // request book info
            StringBuilder bookBuilder = new StringBuilder();
            for (String bookSearchURL : bookURLs) {
                // search urls
                HttpClient bookClient = new DefaultHttpClient();
                try {
                    // get the data
                    HttpGet bookGet = new HttpGet(bookSearchURL);
                    HttpResponse bookResponse = bookClient.execute(bookGet);
                    StatusLine bookSearchStatus = bookResponse.getStatusLine();
                    if (bookSearchStatus.getStatusCode() == 200) {
                        // we have a result
                        HttpEntity bookEntity = bookResponse.getEntity();
                        InputStream bookContent = bookEntity.getContent();
                        InputStreamReader bookInput = new InputStreamReader(bookContent);
                        BufferedReader bookReader = new BufferedReader(bookInput);

                        String lineIn;
                        while ((lineIn = bookReader.readLine()) != null) {
                            bookBuilder.append(lineIn);
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return bookBuilder.toString();
        }

        protected void onPreExecute() {
            dialog = new ProgressDialog(context);
            dialog.setMessage("Working...");
            dialog.show();
        }

        protected void onPostExecute(String result) {
            // parse search results
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            try {
                // parse results
                previewBtn.setVisibility(View.VISIBLE);
                sendBtn.setVisibility(View.VISIBLE);

                JSONObject resultObject = new JSONObject(result);
                JSONArray bookArray = resultObject.getJSONArray("items");
                JSONObject bookObject = bookArray.getJSONObject(0);
                JSONObject volumeObject  = bookObject.getJSONObject("volumeInfo");

                try {
                    titleText.setText("TITLE: " + volumeObject.getString("title"));
                }
                catch (JSONException jse) {
                    titleText.setText("");
                    jse.printStackTrace();
                }

                StringBuilder authorBuild = new StringBuilder("");
                try {
                    JSONArray authorArray = volumeObject.getJSONArray("authors");
                    for (int a=0; a<authorArray.length(); a++) {
                        if (a>0) authorBuild.append(", ");
                        authorBuild.append(authorArray.getString(a));
                    }
                    authorText.setText("AUTHOR(S): " + authorBuild.toString());
                }
                catch (JSONException jse) {
                    authorText.setText("");
                    jse.printStackTrace();
                }

                try {
                    dateText.setText("PUBLISHED: " + volumeObject.getString("publishedDate"));
                }
                catch (JSONException jse) {
                    dateText.setText("");
                    jse.printStackTrace();
                }

                try {
                    descriptionText.setText("DESCRIPTION: " + volumeObject.getString("description"));
                }
                catch (JSONException jse) {
                    descriptionText.setText("");
                    jse.printStackTrace();
                }

                try {
                    // set stars
                    double decNumStars = Double.parseDouble(volumeObject.getString("averageRating"));
                    int numStars = (int)decNumStars;
                    starLayout.setTag(numStars);
                    starLayout.removeAllViews();

                    for (int s=0; s<numStars; s++) {
                        starViews[s].setImageResource(R.drawable.star);
                        starLayout.addView(starViews[s]);
                    }
                }
                catch (JSONException jse) {
                    starLayout.removeAllViews();;
                    jse.printStackTrace();
                }

                try {
                    ratingCountText.setText(" - " + volumeObject.getString("ratingCount") + "ratings");
                }
                catch (JSONException jse) {
                    ratingCountText.setText("");
                    jse.printStackTrace();
                }

                try {
                    boolean isEmbeddable = Boolean.parseBoolean(bookObject.getJSONObject("accessinfo").getString("embeddable"));
                    if (isEmbeddable) previewBtn.setEnabled(true);
                    else previewBtn.setEnabled(false);
                }
                catch (JSONException jse) {
                    previewBtn.setEnabled(false);
                    jse.printStackTrace();
                }

                try {
                    linkBtn.setTag(volumeObject.getString("infoLink"));
                    linkBtn.setVisibility(View.VISIBLE);
                }
                catch (JSONException jse) {
                    linkBtn.setVisibility(View.GONE);
                    jse.printStackTrace();
                }

                try {
                    JSONObject imageInfo = volumeObject.getJSONObject("imageLinks");
                    new getBookThumb().execute(imageInfo.getString("smallThumbnail"));
                }
                catch (JSONException jse) {
                    thumbView.setImageBitmap(null);
                    jse.printStackTrace();
                }
            }
            catch (Exception e) {
                // no result
                e.printStackTrace();
                titleText.setText("NOT FOUND");
                authorText.setText("");
                descriptionText.setText("");
                dateText.setText("");
                starLayout.removeAllViews();
                ratingCountText.setText("");
                thumbView.setImageBitmap(null);
                previewBtn.setVisibility(View.GONE);
                sendBtn.setVisibility(View.VISIBLE);
            }
        }
    }

    private class getBookThumb extends AsyncTask<String, Void, String> {
        // get thumbnail
        @Override
        protected String doInBackground(String... thumbURLs) {
            // attempt to download image
            try {
                // try to download
                URL thumbURL = new URL(thumbURLs[0]);
                URLConnection thumbConn = thumbURL.openConnection();
                thumbConn.connect();

                InputStream thumbIn = thumbConn.getInputStream();
                BufferedInputStream thumbBuff = new BufferedInputStream(thumbIn);

                thumbImg = BitmapFactory.decodeStream(thumbBuff);

                thumbBuff.close();
                thumbIn.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return "";
        }

        protected void onPostExecute(String result) {
            thumbView.setImageBitmap(thumbImg);
        }
    }

    private class sendISBN extends AsyncTask<String, Void, String> {
        Context context;
        private ProgressDialog dialog;
        // add ISBN to database via API

        public sendISBN(Activity activity) {
            this.context = activity;
        }

        @Override
        protected String doInBackground(String... isbnCodes) {
            HttpClient isbnClient = new DefaultHttpClient();
            try {
                // get the data
                Log.v("SCAN", "API: Add to DB-Request: http://172.16.12.3/mediadb/api.php/1/" + isbnCodes[0]);
                HttpGet isbnAdd = new HttpGet("http://172.16.12.3/mediadb/api.php/1/" + isbnCodes[0]);
                HttpResponse isbnResponse = isbnClient.execute(isbnAdd);
                StatusLine isbnAddStatus = isbnResponse.getStatusLine();
                if (isbnAddStatus.getStatusCode() == 200) {
                    // successfully added isbn to database
                    Log.v("SCAN", "API: Add to DB successful.");
                    errorCode = "API: Successful";
                } else {
                    // shit happens
                    Log.v("SCAN", "API: Error on adding to DB! (" + isbnAddStatus.getReasonPhrase() + ")");
                    errorCode = "API: Error " + isbnAddStatus.getStatusCode();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return "";
        }

        protected void onPreExecute() {
            dialog = new ProgressDialog(context);
            dialog.setMessage("Working...");
            dialog.show();
        }

        protected void onPostExecute(String result) {
            if (dialog.isShowing()) {
                dialog.dismiss();
                titleText.setText(errorCode);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }
}
