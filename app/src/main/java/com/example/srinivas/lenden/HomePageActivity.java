package com.example.srinivas.lenden;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.example.srinivas.lenden.database.DBAdapter;
import com.example.srinivas.lenden.dbrequests.ContactDetailsRequest;
import com.example.srinivas.lenden.dbrequests.GroupDetailRequest;
import com.example.srinivas.lenden.dbrequests.TransactionsRequest;
import com.example.srinivas.lenden.dbrequests.UserDetailsRequest;
import com.example.srinivas.lenden.objects.Group;
import com.example.srinivas.lenden.objects.Transaction;
import com.example.srinivas.lenden.objects.User;
import com.example.srinivas.lenden.requests.AsyncRequestListener;
import com.example.srinivas.testlogin.R;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by srinivas on 3/2/2016.
 */
public class HomePageActivity extends AppCompatActivity implements AsyncRequestListener {

    ListView feedView;
    public static User currentUser;
    private Long userId;
    private User user;
    private ArrayList<User> contacts;
    private ArrayList<User> otherUsers;
    private ArrayList<Transaction> transactions;
    private ArrayList<Group> groups;
    private HashMap<Long, User> user_map;
    NumberFormat amtFormatter = new DecimalFormat("#0.00");
    private DBAdapter dbAdapter;
    private static final int CONTACTS_ACTIVITY_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbAdapter = new DBAdapter(this);
        setContentView(R.layout.home_page_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        this.userId = getIntent().getLongExtra("userId", 0);
        this.getUserDetails();
        this.otherUsers = dbAdapter.userHelper.getOtherUsers();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void open_pay_window(View view) {
        Intent pay_activity = new Intent(this, PayReceiveActivity.class);
        pay_activity.putExtra("other_users", this.otherUsers);
        pay_activity.putExtra("user", this.user);
        pay_activity.putExtra("contacts", this.contacts);
        startActivity(pay_activity);
    }

    public void open_contacts_window(View view) {
        Intent contacts_activity = new Intent(this, ContactsActivity.class);
        contacts_activity.putExtra("contacts", this.contacts);
        contacts_activity.putExtra("otherUsers", this.otherUsers);
        startActivityForResult(contacts_activity, CONTACTS_ACTIVITY_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == CONTACTS_ACTIVITY_CODE) {
            if(resultCode == RESULT_OK) {
                Object contacts = data.getSerializableExtra("contacts");
                Object otherUsers = data.getSerializableExtra("otherUsers");

                if(contacts != null && otherUsers != null) {
                    this.contacts = (ArrayList<User>) contacts;
                    this.otherUsers = (ArrayList<User>) otherUsers;
                }
            }
        }
    }

    public void open_groups_window(View view) {
        Intent group_activity = new Intent(this, GroupsActivity.class);
        group_activity.putExtra("groups", this.groups);
        group_activity.putExtra("source", "fromHomePage");
        startActivity(group_activity);
    }

    @Override
    public void onResponseReceived(String name, HashMap response) {
        if(name.equals("UserDetailsRequest")) {
            this.userDetailsResponseReceived(response);
        } else if(name.equals("ContactsDetailsRequest")) {
            this.contactDetailsReceived(response);
        } else if(name.equals("TransactionsRequest")) {
            this.transactionDetailsReceived(response);
        } else if(name.equals("GroupsRequest")) {
            this.groupsReceived(response);
        }
    }

    private void getUserDetails() {
        UserDetailsRequest user_req = new UserDetailsRequest(this, getApplicationContext());
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("user_id", this.userId);
        user_req.sendRequest(payload);
    }

    private void getContacts() {
        ContactDetailsRequest contacts_req = new ContactDetailsRequest(this, getApplicationContext());
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("contact_ids", this.user.getContacts());
        contacts_req.sendRequest(payload);
    }

    private void getGroups() {
        GroupDetailRequest group_req = new GroupDetailRequest(this, getApplicationContext());
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("user_id", this.user.getId());
        group_req.sendRequest(payload);
    }

    private void getTransactions() {
        TransactionsRequest trans_req = new TransactionsRequest(this, getApplicationContext());
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("user_id", this.user.getId());
        trans_req.sendRequest(payload);
    }

    private void userDetailsResponseReceived(HashMap response) {
        this.user = (User) response.get("user");
        currentUser = this.user;
        Toast.makeText(getApplicationContext(), "Welcome " + this.user.getName(), Toast.LENGTH_LONG).show();
        this.getContacts();
    }

    private void contactDetailsReceived(HashMap response) {
        this.contacts = (ArrayList<User>) response.get("contacts");
        this.getGroups();
    }

    private void transactionDetailsReceived(HashMap response) {
        this.transactions = (ArrayList<Transaction>) response.get("transactions");
        this.formUserMap();
        this.setAdapters();
    }

    private void groupsReceived(HashMap response) {
        this.groups = (ArrayList<Group>) response.get("groups");
        this.getTransactions();
    }

    private void setAdapters() {
        feedView = (ListView) findViewById(R.id.feed_view);
        // reminderView = (ListView) findViewById(R.id.reminder_view);

        ArrayList<String> transactions = this.getTransactionsSummary();
        ArrayAdapter feedAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_activated_1,
                transactions);
        feedView.setAdapter(feedAdapter);
    }

    private ArrayList<String> getTransactionsSummary() {
        ArrayList<String> result = new ArrayList<>();
        String conj = "";
        for(int i=0; i < this.transactions.size(); i++) {
            String s="";
            Long id;
            Transaction t = this.transactions.get(i);
            if(t.getSourceId().equals(this.userId)) {
                s += "You paid ";
                id = t.getDestId();
                conj = "to " + this.user_map.get(id).get_user_name() + " for " + t.getDescription();
            } else {
                s += "You received ";
                id = t.getSourceId();
                conj = "from " + this.user_map.get(id).get_user_name() + " for " + t.getDescription();
            }
            result.add(s + amtFormatter.format(t.getAmount()) + " " + conj);
        }
        return result;
    }

    private void formUserMap() {
        HashMap<Long, User> user_map = new HashMap<>();
        for (int i=0; i < this.contacts.size(); i++) {
            User u = this.contacts.get(i);
            user_map.put(u.getId(), u);
        }
        this.user_map = user_map;
    }


}
