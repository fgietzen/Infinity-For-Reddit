package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.util.Linkify;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.r0adkll.slidr.Slidr;
import com.r0adkll.slidr.model.SlidrInterface;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.recycler.MarkwonAdapter;
import io.noties.markwon.recycler.table.TableEntry;
import io.noties.markwon.recycler.table.TableEntryPlugin;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.MarkwonLinearLayoutManager;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class WikiActivity extends BaseActivity {

    public static final String EXTRA_SUBREDDIT_NAME = "ESN";
    private static final String WIKI_MARKDOWN_STATE = "WMS";

    @BindView(R.id.coordinator_layout_comment_wiki_activity)
    CoordinatorLayout coordinatorLayout;
    @BindView(R.id.appbar_layout_comment_wiki_activity)
    AppBarLayout appBarLayout;
    @BindView(R.id.toolbar_comment_wiki_activity)
    Toolbar toolbar;
    @BindView(R.id.content_markdown_view_comment_wiki_activity)
    RecyclerView markdownRecyclerView;
    @Inject
    @Named("no_oauth")
    Retrofit retrofit;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    private SlidrInterface mSlidrInterface;
    private String wikiMarkdown;
    private Markwon markwon;
    private MarkwonAdapter markwonAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wiki);

        ButterKnife.bind(this);

        EventBus.getDefault().register(this);

        applyCustomTheme();

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (mSharedPreferences.getBoolean(SharedPreferencesUtils.SWIPE_RIGHT_TO_GO_BACK, true)) {
            mSlidrInterface = Slidr.attach(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(appBarLayout);
            }

            if (isImmersiveInterface()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    coordinatorLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }
                adjustToolbar(toolbar);
                markdownRecyclerView.setPadding(markdownRecyclerView.getPaddingLeft(), 0, markdownRecyclerView.getPaddingRight(), getNavBarHeight());
            }
        }

        int markdownColor = mCustomThemeWrapper.getPrimaryTextColor();
        int spoilerBackgroundColor = markdownColor | 0xFF000000;
        int linkColor = mCustomThemeWrapper.getLinkColor();
        markwon = Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @NonNull
                    @Override
                    public String processMarkdown(@NonNull String markdown) {
                        StringBuilder markdownStringBuilder = new StringBuilder(markdown);
                        Pattern spoilerPattern = Pattern.compile(">![\\S\\s]*?!<");
                        Matcher matcher = spoilerPattern.matcher(markdownStringBuilder);
                        while (matcher.find()) {
                            markdownStringBuilder.replace(matcher.start(), matcher.start() + 1, "&gt;");
                        }
                        return super.processMarkdown(markdownStringBuilder.toString());
                    }

                    @Override
                    public void afterSetText(@NonNull TextView textView) {
                        textView.setHighlightColor(Color.TRANSPARENT);
                        SpannableStringBuilder markdownStringBuilder = new SpannableStringBuilder(textView.getText().toString());
                        Pattern spoilerPattern = Pattern.compile(">![\\S\\s]*?!<");
                        Matcher matcher = spoilerPattern.matcher(markdownStringBuilder);
                        int start = 0;
                        boolean find = false;
                        while (matcher.find(start)) {
                            if (markdownStringBuilder.length() < 4
                                    || matcher.start() < 0
                                    || matcher.end() > markdownStringBuilder.length()) {
                                break;
                            }
                            find = true;
                            markdownStringBuilder.delete(matcher.end() - 2, matcher.end());
                            markdownStringBuilder.delete(matcher.start(), matcher.start() + 2);
                            ClickableSpan clickableSpan = new ClickableSpan() {
                                private boolean isShowing = false;
                                @Override
                                public void updateDrawState(@NonNull TextPaint ds) {
                                    if (isShowing) {
                                        super.updateDrawState(ds);
                                        ds.setColor(markdownColor);
                                    } else {
                                        ds.bgColor = spoilerBackgroundColor;
                                        ds.setColor(markdownColor);
                                    }
                                    ds.setUnderlineText(false);
                                }

                                @Override
                                public void onClick(@NonNull View view) {
                                    isShowing = !isShowing;
                                    view.invalidate();
                                }
                            };
                            markdownStringBuilder.setSpan(clickableSpan, matcher.start(), matcher.end() - 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            start = matcher.end() - 4;
                        }
                        if (find) {
                            textView.setText(markdownStringBuilder);
                        }
                    }

                    @Override
                    public void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown) {
                        textView.setTextColor(markdownColor);
                    }

                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.linkResolver((view, link) -> {
                            Intent intent = new Intent(WikiActivity.this, LinkResolverActivity.class);
                            Uri uri = Uri.parse(link);
                            intent.setData(uri);
                            startActivity(intent);
                        });
                    }

                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.linkColor(linkColor);
                    }
                })
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
                .usePlugin(TableEntryPlugin.create(this))
                .build();

        markwonAdapter = MarkwonAdapter.builder(R.layout.adapter_default_entry, R.id.text)
                .include(TableBlock.class, TableEntry.create(builder -> builder
                        .tableLayout(R.layout.adapter_table_block, R.id.table_layout)
                        .textLayoutIsRoot(R.layout.view_table_entry_cell)))
                .build();
        LinearLayoutManager linearLayoutManager = new MarkwonLinearLayoutManager(this, new MarkwonLinearLayoutManager.HorizontalScrollViewScrolledListener() {
            @Override
            public void onScrolledLeft() {
                if (mSlidrInterface != null) {
                    mSlidrInterface.lock();
                }
            }

            @Override
            public void onScrolledRight() {
                if (mSlidrInterface != null) {
                    mSlidrInterface.unlock();
                }
            }
        });
        markdownRecyclerView.setLayoutManager(linearLayoutManager);
        markdownRecyclerView.setAdapter(markwonAdapter);

        if (savedInstanceState != null) {
            wikiMarkdown = savedInstanceState.getString(WIKI_MARKDOWN_STATE);
        }

        if (wikiMarkdown == null) {
            loadWiki();
        } else {
            markwonAdapter.setMarkdown(markwon, wikiMarkdown);
            markwonAdapter.notifyDataSetChanged();
        }
    }

    private void loadWiki() {
        retrofit.create(RedditAPI.class).getWiki(getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME)).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        markwonAdapter.setMarkdown(markwon, new JSONObject(response.body())
                                .getJSONObject(JSONUtils.DATA_KEY).getString(JSONUtils.CONTENT_MD_KEY));
                        markwonAdapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {

            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return false;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(WIKI_MARKDOWN_STATE, wikiMarkdown);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    protected CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        coordinatorLayout.setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndToolbarTheme(appBarLayout, toolbar);
    }

    @Subscribe
    public void onAccountSwitchEvent(SwitchAccountEvent event) {
        if (!getClass().getName().equals(event.excludeActivityClassName)) {
            finish();
        }
    }
}