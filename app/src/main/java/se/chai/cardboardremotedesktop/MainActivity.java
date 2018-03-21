package se.chai.cardboardremotedesktop;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ServerList.getServerList().load(this);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        
        AdLogic ads = new AdLogic();
        ads.loadAds(this);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new ServerListFragment())
                    .commit();
        }

    }


    @Override
    protected void onPause() {
        super.onPause();
        ServerList.getServerList().save(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);

        }else if (id == R.id.action_about) {
            View messageView = getLayoutInflater().inflate(R.layout.about, null, false);

            TextView textView = null;
            if (!BuildConfig.FLAVOR.equals(getString(R.string.variant_name_pro))) {
                textView = (TextView) messageView.findViewById(R.id.about_fullversion);
                textView.setText(Html.fromHtml(getString(R.string.app_about_fullversion), Html.FROM_HTML_MODE_LEGACY));
            }

            TextView textView2 = (TextView) messageView.findViewById(R.id.about_credits);
            textView2.setText(Html.fromHtml(getString(R.string.app_credits), Html.FROM_HTML_MODE_LEGACY));

            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.app_name))
                    .setIcon(R.drawable.ic_launcher)
                    .setView(messageView)
                    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Do nothing.
                        }
                    })
                    .show();


            if (textView != null)
                textView.setMovementMethod(LinkMovementMethod.getInstance());
            textView2.setMovementMethod(LinkMovementMethod.getInstance());

//        } else if (id == R.id.action_purchase) {
//            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.android.vending");
//            ComponentName comp = new ComponentName("com.android.vending", "com.google.android.finsky.activities.LaunchUrlHandlerActivity"); // package name and activity
//            launchIntent.setComponent(comp);
//            launchIntent.setData(Uri.parse("market://details?id=se.chai.vrtv"));
//
//            startActivity(launchIntent);
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class ServerListFragment extends Fragment implements View.OnClickListener {

        private CardAdapter adapter;

        public ServerListFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            final FragmentActivity c = getActivity();
            final RecyclerView recyclerView = (RecyclerView) rootView.findViewById(R.id.card_list);
            LinearLayoutManager layoutManager = new LinearLayoutManager(c);
            layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            recyclerView.setLayoutManager(layoutManager);

            adapter = new CardAdapter();
            recyclerView.setAdapter(adapter);

            ImageButton addButton = (ImageButton) rootView.findViewById(R.id.button);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                addButton.setBackgroundResource(R.drawable.fab);
                addButton.setTranslationZ(5);
            } else {
                addButton.setBackgroundResource(R.drawable.fab_shadow);
            }

            addButton.setOnClickListener(this);

            return rootView;
        }

        @Override
        public void onClick(View v) {
            Intent myIntent = new Intent(v.getContext(), EditActivity.class);
            v.getContext().startActivity(myIntent);
        }

    }
}
