package com.keylesspalace.tusky;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener {
    private String domain = null;
    private String accessToken = null;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NotificationsAdapter adapter;

    public static NotificationsFragment newInstance() {
        NotificationsFragment fragment = new NotificationsFragment();
        Bundle arguments = new Bundle();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_timeline, container, false);

        Context context = getContext();
        SharedPreferences preferences = context.getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
        assert(domain != null);
        assert(accessToken != null);

        // Setup the SwipeRefreshLayout.
        swipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this);
        // Setup the RecyclerView.
        RecyclerView recyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration divider = new DividerItemDecoration(
                context, layoutManager.getOrientation());
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.status_divider);
        divider.setDrawable(drawable);
        recyclerView.addItemDecoration(divider);
        EndlessOnScrollListener scrollListener = new EndlessOnScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                NotificationsAdapter adapter = (NotificationsAdapter) view.getAdapter();
                String fromId = adapter.getItem(adapter.getItemCount() - 1).getId();
                sendFetchNotificationsRequest(fromId);
            }
        };
        recyclerView.addOnScrollListener(scrollListener);
        adapter = new NotificationsAdapter();
        recyclerView.setAdapter(adapter);

        sendFetchNotificationsRequest();

        return rootView;
    }

    private void sendFetchNotificationsRequest(final String fromId) {
        String endpoint = getString(R.string.endpoint_notifications);
        String url = "https://" + domain + endpoint;
        if (fromId != null) {
            url += "?max_id=" + fromId;
        }
        JsonArrayRequest request = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        List<Notification> notifications = new ArrayList<>();
                        try {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject object = response.getJSONObject(i);
                                String id = object.getString("id");
                                Notification.Type type = Notification.Type.valueOf(
                                        object.getString("type").toUpperCase());
                                JSONObject account = object.getJSONObject("account");
                                String displayName = account.getString("display_name");
                                Notification notification = new Notification(type, id, displayName);
                                notifications.add(notification);
                            }
                            onFetchNotificationsSuccess(notifications, fromId != null);
                        } catch (JSONException e) {
                            onFetchNotificationsFailure(e);
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onFetchNotificationsFailure(error);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(getContext()).addToRequestQueue(request);
    }

    private void sendFetchNotificationsRequest() {
        sendFetchNotificationsRequest(null);
    }

    private void onFetchNotificationsSuccess(List<Notification> notifications, boolean added) {
        if (added) {
            adapter.addItems(notifications);
        } else {
            adapter.update(notifications);
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    private void onFetchNotificationsFailure(Exception exception) {
        Toast.makeText(getContext(), R.string.error_fetching_notifications, Toast.LENGTH_SHORT)
                .show();
        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onRefresh() {
        sendFetchNotificationsRequest();
    }
}
