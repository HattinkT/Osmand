package net.osmand.plus.search;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.osmand.AndroidUtils;
import net.osmand.osm.AbstractPoiType;
import net.osmand.osm.MapPoiTypes;
import net.osmand.osm.PoiCategory;
import net.osmand.osm.PoiFilter;
import net.osmand.osm.PoiType;
import net.osmand.plus.IconsCache;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.dialogs.DirectionsDialogs;
import net.osmand.plus.poi.PoiUIFilter;
import net.osmand.plus.render.RenderingIcons;
import net.osmand.plus.widgets.TextViewEx;
import net.osmand.util.Algorithms;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class QuickSearchPoiFilterFragment extends DialogFragment {
	public static final String TAG = "QuickSearchPoiFilterFragment";

	private static final String QUICK_SEARCH_POI_FILTER_ID_KEY = "quick_search_poi_filter_id_key";
	private static final String QUICK_SEARCH_POI_FILTER_BY_NAME_KEY = "quick_search_poi_filter_by_name_key";
	private static final String QUICK_SEARCH_POI_FILTER_SELECTED_ADDITIONALS = "quick_search_poi_filter_selected_additionals";
	private static final String QUICK_SEARCH_POI_FILTER_COLLAPSED_CATEGORIES = "quick_search_poi_filter_collapsed_categories";
	private static final String QUICK_SEARCH_POI_FILTER_SHOW_ALL_CATEGORIES = "quick_search_poi_filter_show_all_categories";

	private View view;
	private ListView listView;
	private PoiFilterListAdapter adapter;
	private PoiUIFilter filter;
	private String filterId;
	private String nameFilterText = "";
	private String nameFilterTextOrig = "";
	private EditText editText;
	private TextView applyFilterButton;
	private View applyFilterButtonShadow;
	private Set<String> selectedPoiAdditionals = new TreeSet<>();
	private Set<String> selectedPoiAdditionalsOrig = new TreeSet<>();
	private ArrayList<String> collapsedCategories = new ArrayList<>();
	private ArrayList<String> showAllCategories = new ArrayList<>();
	private Map<PoiType, String> poiAdditionalsTranslations = new HashMap<>();

	public QuickSearchPoiFilterFragment() {
	}

	private OsmandApplication getMyApplication() {
		return (OsmandApplication) getActivity().getApplication();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		boolean isLightTheme = getMyApplication().getSettings().OSMAND_THEME.get() == OsmandSettings.OSMAND_LIGHT_THEME;
		int themeId = isLightTheme ? R.style.OsmandLightTheme : R.style.OsmandDarkTheme;
		setStyle(STYLE_NO_FRAME, themeId);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		final OsmandApplication app = getMyApplication();

		if (getArguments() != null) {
			filterId = getArguments().getString(QUICK_SEARCH_POI_FILTER_ID_KEY);
			nameFilterText = getArguments().getString(QUICK_SEARCH_POI_FILTER_BY_NAME_KEY);
		} else if (savedInstanceState != null) {
			filterId = savedInstanceState.getString(QUICK_SEARCH_POI_FILTER_ID_KEY);
			nameFilterText = savedInstanceState.getString(QUICK_SEARCH_POI_FILTER_BY_NAME_KEY);
			ArrayList<String> selectedList = savedInstanceState.getStringArrayList(QUICK_SEARCH_POI_FILTER_SELECTED_ADDITIONALS);
			if (selectedList != null) {
				selectedPoiAdditionals.addAll(selectedList);
			}
			ArrayList<String> collapsedList = savedInstanceState.getStringArrayList(QUICK_SEARCH_POI_FILTER_COLLAPSED_CATEGORIES);
			if (collapsedList != null) {
				collapsedCategories.addAll(collapsedList);
			}
			ArrayList<String> showAllList = savedInstanceState.getStringArrayList(QUICK_SEARCH_POI_FILTER_SHOW_ALL_CATEGORIES);
			if (showAllList != null) {
				showAllCategories.addAll(showAllList);
			}
		}

		nameFilterTextOrig = "" + nameFilterText;

		if (filterId != null) {
			filter = app.getPoiFilters().getFilterById(filterId);
		}
		if (filter == null) {
			filter = app.getPoiFilters().getCustomPOIFilter();
			filter.clearFilter();
		}
		if (selectedPoiAdditionals.size() == 0) {
			processFilterFields();
			initListItems();
		}
		selectedPoiAdditionalsOrig = new TreeSet<>(selectedPoiAdditionals);

		view = inflater.inflate(R.layout.search_poi_filter, container, false);

		app.changeKeyguardFlags(getDialog().getWindow());

		TextView description = (TextView) view.findViewById(R.id.description);
		description.setText(filter.getName());

		Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
		toolbar.setNavigationIcon(app.getIconsCache().getIcon(R.drawable.ic_action_remove_dark));
		toolbar.setNavigationContentDescription(R.string.shared_string_close);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		ImageButton moreButton = (ImageButton) view.findViewById(R.id.moreButton);
		moreButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				IconsCache iconsCache = app.getIconsCache();
				final PopupMenu optionsMenu = new PopupMenu(getContext(), v);
				DirectionsDialogs.setupPopUpMenuIcon(optionsMenu);
				MenuItem item;

				if (!filter.isStandardFilter()) {
					item = optionsMenu.getMenu().add(R.string.edit_filter).setIcon(
							iconsCache.getThemedIcon(R.drawable.ic_action_edit_dark));
					item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							editFilter();
							return true;
						}
					});
				}

				if (!filter.isStandardFilter()) {
					item = optionsMenu.getMenu().add(R.string.edit_filter_save_as_menu_item).setIcon(
							iconsCache.getThemedIcon(R.drawable.ic_action_save));
				} else {
					item = optionsMenu.getMenu().add(R.string.save_filter).setIcon(
							iconsCache.getThemedIcon(R.drawable.ic_action_save));
				}
				item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						saveFilter();
						return true;
					}
				});

				if (!filter.isStandardFilter()) {
					item = optionsMenu.getMenu().add(R.string.delete_filter)
							.setIcon(iconsCache.getThemedIcon(R.drawable.ic_action_delete_dark));
					item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							deleteFilter();
							return true;
						}
					});
				}

				optionsMenu.show();
			}
		});

		listView = (ListView) view.findViewById(android.R.id.list);
		listView.setBackgroundColor(getResources().getColor(
				app.getSettings().isLightContent() ? R.color.ctx_menu_info_view_bg_light
						: R.color.ctx_menu_info_view_bg_dark));

		View editTextView = inflater.inflate(R.layout.poi_filter_list_item, listView, false);
		editText = (EditText) editTextView.findViewById(R.id.editText);
		editTextView.findViewById(R.id.divider).setVisibility(View.GONE);
		editText.setText(nameFilterText);
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				nameFilterText = s.toString();
				updateApplyButton();
			}
		});

		editText.setVisibility(View.VISIBLE);
		final ImageView textEditIcon = (ImageView) editTextView.findViewById(R.id.icon);
		textEditIcon.setImageDrawable(app.getIconsCache().getThemedIcon(R.drawable.ic_action_search_dark));
		textEditIcon.setVisibility(View.VISIBLE);
		editTextView.findViewById(R.id.titleBold).setVisibility(View.GONE);
		editTextView.findViewById(R.id.titleButton).setVisibility(View.GONE);
		editTextView.findViewById(R.id.expandItem).setVisibility(View.GONE);
		editTextView.findViewById(R.id.titleRegular).setVisibility(View.GONE);
		editTextView.findViewById(R.id.switchItem).setVisibility(View.GONE);
		editTextView.findViewById(R.id.checkboxItem).setVisibility(View.GONE);
		listView.addHeaderView(editTextView);

		View bottomShadowView = inflater.inflate(R.layout.list_shadow_footer, listView, false);
		listView.addFooterView(bottomShadowView, null, false);
		adapter = new PoiFilterListAdapter(app, getListItems());
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				PoiFilterListItem item = adapter.getItem(position - listView.getHeaderViewsCount());
				if (item != null) {
					switch (item.type) {
						case GROUP_HEADER:
							if (item.category != null) {
								if (collapsedCategories.contains(item.category)) {
									collapsedCategories.remove(item.category);
								} else {
									collapsedCategories.add(item.category);
								}
								updateListView();
							}
							break;
						case CHECKBOX_ITEM:
							CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkboxItem);
							adapter.toggleCheckbox(item, checkBox, !checkBox.isChecked());
							break;
						case BUTTON_ITEM:
							if (item.category != null) {
								showAllCategories.add(item.category);
								updateListView();
							}
							break;
					}
				}
			}
		});

		applyFilterButtonShadow = view.findViewById(R.id.bottomButtonShadow);
		applyFilterButton = (TextView) view.findViewById(R.id.bottomButton);
		applyFilterButton.setText(app.getString(R.string.apply_filters));
		applyFilterButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				applyFilterFields();
				if (!filter.isStandardFilter()) {
					filter.setSavedFilterByName(filter.getFilterByName());
					if (app.getPoiFilters().editPoiFilter(filter)) {
						app.getSearchUICore().refreshCustomPoiFilters();
						((QuickSearchDialogFragment) getParentFragment()).replaceQueryWithUiFilter(filter, "");
						((QuickSearchDialogFragment) getParentFragment()).reloadCategories();
						dismiss();
					}
				} else {
					((QuickSearchDialogFragment) getParentFragment()).replaceQueryWithUiFilter(filter, nameFilterText.trim());
					dismiss();
				}
			}
		});
		updateApplyButton();

		return view;
	}

	private void updateApplyButton() {
		boolean hasChanges = !nameFilterText.equals(nameFilterTextOrig) || !selectedPoiAdditionals.equals(selectedPoiAdditionalsOrig);
		applyFilterButton.setVisibility(hasChanges ? View.VISIBLE : View.GONE);
		applyFilterButtonShadow.setVisibility(hasChanges ? View.VISIBLE : View.GONE);
	}

	private void deleteFilter() {
		final OsmandApplication app = getMyApplication();
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setMessage(R.string.edit_filter_delete_dialog_title);
		builder.setNegativeButton(R.string.shared_string_no, null);
		builder.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {

				if (app.getPoiFilters().removePoiFilter(filter)) {
					Toast.makeText(getContext(), MessageFormat.format(getContext().getText(R.string.edit_filter_delete_message).toString(),
							filter.getName()), Toast.LENGTH_SHORT).show();
					app.getSearchUICore().refreshCustomPoiFilters();
					QuickSearchDialogFragment quickSearchDialogFragment = (QuickSearchDialogFragment) getParentFragment();
					quickSearchDialogFragment.reloadCategories();
					quickSearchDialogFragment.clearLastWord();
					QuickSearchPoiFilterFragment.this.dismiss();
				}
			}
		});
		builder.create().show();
	}

	private void editFilter() {
		QuickSearchCustomPoiFragment.showDialog(this, filter.getFilterId());
	}

	private void saveFilter() {
		final OsmandApplication app = getMyApplication();
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.access_hint_enter_name);

		final EditText editText = new EditText(getContext());
		editText.setHint(R.string.new_filter);
		editText.setText(filter.getName());

		final TextView textView = new TextView(getContext());
		textView.setText(app.getString(R.string.new_filter_desc));
		textView.setTextAppearance(getContext(), R.style.TextAppearance_ContextMenuSubtitle);
		LinearLayout ll = new LinearLayout(getContext());
		ll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		ll.setOrientation(LinearLayout.VERTICAL);
		ll.setPadding(AndroidUtils.dpToPx(getContext(), 20f), AndroidUtils.dpToPx(getContext(), 12f), AndroidUtils.dpToPx(getContext(), 20f), AndroidUtils.dpToPx(getContext(), 12f));
		ll.addView(editText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		textView.setPadding(AndroidUtils.dpToPx(getContext(), 4f), AndroidUtils.dpToPx(getContext(), 6f), AndroidUtils.dpToPx(getContext(), 4f), AndroidUtils.dpToPx(getContext(), 4f));
		ll.addView(textView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		builder.setView(ll);

		builder.setNegativeButton(R.string.shared_string_cancel, null);
		builder.setPositiveButton(R.string.shared_string_save, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				PoiUIFilter nFilter = new PoiUIFilter(editText.getText().toString(), null, filter.getAcceptedTypes(), app);
				applyFilterFields();
				if (!Algorithms.isEmpty(filter.getFilterByName())) {
					nFilter.setSavedFilterByName(filter.getFilterByName());
				}
				if (app.getPoiFilters().createPoiFilter(nFilter)) {
					Toast.makeText(getContext(), MessageFormat.format(getContext().getText(R.string.edit_filter_create_message).toString(),
							editText.getText().toString()), Toast.LENGTH_SHORT).show();
					app.getSearchUICore().refreshCustomPoiFilters();
					((QuickSearchDialogFragment) getParentFragment()).replaceQueryWithUiFilter(nFilter, "");
					((QuickSearchDialogFragment) getParentFragment()).reloadCategories();
					QuickSearchPoiFilterFragment.this.dismiss();
				}
			}
		});
		builder.create().show();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(QUICK_SEARCH_POI_FILTER_ID_KEY, filterId);
		outState.putString(QUICK_SEARCH_POI_FILTER_BY_NAME_KEY, nameFilterText);
		outState.putStringArrayList(QUICK_SEARCH_POI_FILTER_SELECTED_ADDITIONALS, new ArrayList<>(selectedPoiAdditionals));
		outState.putStringArrayList(QUICK_SEARCH_POI_FILTER_COLLAPSED_CATEGORIES, collapsedCategories);
		outState.putStringArrayList(QUICK_SEARCH_POI_FILTER_SHOW_ALL_CATEGORIES, showAllCategories);
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		hideKeyboard();
		super.onDismiss(dialog);
	}

	private void hideKeyboard() {
		if (editText.hasFocus()) {
			AndroidUtils.hideSoftKeyboard(getActivity(), editText);
		}
	}

	private void updateListView() {
		adapter.setListItems(getListItems());
	}

	private void applyFilterFields() {
		StringBuilder sb = new StringBuilder();
		if (!Algorithms.isEmpty(nameFilterText)) {
			sb.append(nameFilterText);
		}
		for (String param : selectedPoiAdditionals) {
			if (sb.length() > 0) {
				sb.append(" ");
			}
			sb.append(param);
		}
		filter.setFilterByName(sb.toString());
	}

	private void processFilterFields() {
		OsmandApplication app = getMyApplication();
		String filterByName = filter.getFilterByName();
		if (!Algorithms.isEmpty(filterByName)) {
			int index;
			MapPoiTypes poiTypes = app.getPoiTypes();
			Map<String, PoiType> poiAdditionals = filter.getPoiAdditionals();
			Set<String> excludedPoiAdditionalCategories = getExcludedPoiAdditionalCategories();
			List<PoiType> otherAdditionalCategories = poiTypes.getOtherMapCategory().getPoiAdditionalsCategorized();

			if (!excludedPoiAdditionalCategories.contains("opening_hours")) {
				String keyNameOpen = app.getString(R.string.shared_string_is_open).replace(' ', '_').toLowerCase();
				String keyNameOpen24 = app.getString(R.string.shared_string_is_open_24_7).replace(' ', '_').toLowerCase();
				index = filterByName.indexOf(keyNameOpen24);
				if (index != -1) {
					selectedPoiAdditionals.add(keyNameOpen24);
					filterByName = filterByName.replaceAll(keyNameOpen24, "");
				}
				index = filterByName.indexOf(keyNameOpen);
				if (index != -1) {
					selectedPoiAdditionals.add(keyNameOpen);
					filterByName = filterByName.replaceAll(keyNameOpen, "");
				}
			}
			if (poiAdditionals != null) {
				Map<String, List<PoiType>> additionalsMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				extractPoiAdditionals(poiAdditionals.values(), additionalsMap, excludedPoiAdditionalCategories, true);
				extractPoiAdditionals(otherAdditionalCategories, additionalsMap, excludedPoiAdditionalCategories, true);

				if (additionalsMap.size() > 0) {
					for (Entry<String, List<PoiType>> entry : additionalsMap.entrySet()) {
						for (PoiType poiType : entry.getValue()) {
							String keyName = poiType.getKeyName().replace('_', ':').toLowerCase();
							index = filterByName.indexOf(keyName);
							if (index != -1) {
								selectedPoiAdditionals.add(keyName);
								filterByName = filterByName.replaceAll(keyName, "");
							}
						}
					}
				}
			}
			if (filterByName.trim().length() > 0 && Algorithms.isEmpty(nameFilterText)) {
				nameFilterText = filterByName.trim();
			}
		}
	}

	@NonNull
	private Set<String> getExcludedPoiAdditionalCategories() {
		Set<String> excludedPoiAdditionalCategories = new LinkedHashSet<>();
		if (filter.getAcceptedTypes().size() == 0) {
			return excludedPoiAdditionalCategories;
		}
		MapPoiTypes poiTypes = getMyApplication().getPoiTypes();
		PoiCategory topCategory = null;
		for (Entry<PoiCategory, LinkedHashSet<String>> entry : filter.getAcceptedTypes().entrySet()) {
			if (topCategory == null) {
				topCategory = entry.getKey();
			}
			if (entry.getValue() != null) {
				Set<String> excluded = new LinkedHashSet<>();
				for (String keyName : entry.getValue()) {
					PoiType poiType = poiTypes.getPoiTypeByKeyInCategory(topCategory, keyName);
					if (poiType != null) {
						collectExcludedPoiAdditionalCategories(poiType, excluded);
						if (!poiType.isReference()) {
							PoiFilter poiFilter = poiType.getFilter();
							if (poiFilter != null) {
								collectExcludedPoiAdditionalCategories(poiFilter, excluded);
							}
							PoiCategory poiCategory = poiType.getCategory();
							if (poiCategory != null) {
								collectExcludedPoiAdditionalCategories(poiCategory, excluded);
							}
						}
					}
					if (excludedPoiAdditionalCategories.size() == 0) {
						excludedPoiAdditionalCategories.addAll(excluded);
					} else {
						excludedPoiAdditionalCategories.retainAll(excluded);
					}
					excluded.clear();
				}
			}
		}
		if (topCategory != null && topCategory.getExcludedPoiAdditionalCategories() != null) {
			excludedPoiAdditionalCategories.addAll(topCategory.getExcludedPoiAdditionalCategories());
		}

		return excludedPoiAdditionalCategories;
	}

	private void collectExcludedPoiAdditionalCategories(AbstractPoiType abstractPoiType,
														Set<String> excludedPoiAdditionalCategories) {
		List<String> categories = abstractPoiType.getExcludedPoiAdditionalCategories();
		if (categories != null) {
			excludedPoiAdditionalCategories.addAll(categories);
		}
	}

	private List<PoiFilterListItem> getListItems() {
		OsmandApplication app = getMyApplication();
		MapPoiTypes poiTypes = app.getPoiTypes();

		int groupId = 0;
		List<PoiFilterListItem> items = new ArrayList<>();

		Map<String, PoiType> poiAdditionals = filter.getPoiAdditionals();
		Set<String> excludedPoiAdditionalCategories = getExcludedPoiAdditionalCategories();
		List<PoiType> otherAdditionalCategories = poiTypes.getOtherMapCategory().getPoiAdditionalsCategorized();

		if (!excludedPoiAdditionalCategories.contains("opening_hours")) {
			items.add(new PoiFilterListItem(PoiFilterListItemType.DIVIDER, 0, null, -1, false, false, false, null, null));
			String keyNameOpen = app.getString(R.string.shared_string_is_open).replace(' ', '_').toLowerCase();
			items.add(new PoiFilterListItem(PoiFilterListItemType.SWITCH_ITEM,
					R.drawable.ic_action_time, app.getString(R.string.shared_string_is_open), ++groupId,
					false, false, selectedPoiAdditionals.contains(keyNameOpen), null, keyNameOpen));
			String keyNameOpen24 = app.getString(R.string.shared_string_is_open_24_7).replace(' ', '_').toLowerCase();
			items.add(new PoiFilterListItem(PoiFilterListItemType.SWITCH_ITEM,
					0, app.getString(R.string.shared_string_is_open_24_7), groupId, false, false,
					selectedPoiAdditionals.contains(keyNameOpen24), null, keyNameOpen24));
		}
		if (poiAdditionals != null) {
			Map<String, List<PoiType>> additionalsMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			extractPoiAdditionals(poiAdditionals.values(), additionalsMap, excludedPoiAdditionalCategories, false);
			extractPoiAdditionals(otherAdditionalCategories, additionalsMap, excludedPoiAdditionalCategories, false);

			if (additionalsMap.size() > 0) {
				for (Entry<String, List<PoiType>> entry : additionalsMap.entrySet()) {
					String category = entry.getKey();
					String categoryLocalizedName = poiTypes.getPoiTranslation(category);
					boolean expanded = !collapsedCategories.contains(category);
					boolean showAll = showAllCategories.contains(category);
					items.add(new PoiFilterListItem(PoiFilterListItemType.DIVIDER, 0, null, -1, false, false, false, null, null));

					String categoryIconStr = poiTypes.getPoiAdditionalCategoryIcon(category);
					int categoryIconId = 0;
					if (!Algorithms.isEmpty(categoryIconStr)) {
						categoryIconId = RenderingIcons.getBigIconResourceId(categoryIconStr);
					}
					if (categoryIconId == 0) {
						categoryIconId = getResources().getIdentifier("mx_" + category, "drawable", app.getPackageName());
					}
					if (categoryIconId == 0) {
						categoryIconId = R.drawable.ic_action_folder_stroke;
					}

					items.add(new PoiFilterListItem(PoiFilterListItemType.GROUP_HEADER,
							categoryIconId, categoryLocalizedName, ++groupId, true, expanded, false, category, null));
					List<PoiType> categoryPoiAdditionals = new ArrayList<>(entry.getValue());
					Collections.sort(categoryPoiAdditionals, new Comparator<PoiType>() {
						@Override
						public int compare(PoiType p1, PoiType p2) {
							String firstPoiTypeTranslation = poiAdditionalsTranslations.get(p1);
							String secondPoiTypeTranslation = poiAdditionalsTranslations.get(p2);
							if (firstPoiTypeTranslation != null && secondPoiTypeTranslation != null) {
								return firstPoiTypeTranslation.compareTo(secondPoiTypeTranslation);
							} else {
								return 0;
							}
						}
					});
					for (PoiType poiType : categoryPoiAdditionals) {
						String keyName = poiType.getKeyName().replace('_', ':').toLowerCase();
						String translation = poiAdditionalsTranslations.get(poiType);
						items.add(new PoiFilterListItem(PoiFilterListItemType.CHECKBOX_ITEM,
								0, translation, groupId, false, false, selectedPoiAdditionals.contains(keyName), category, keyName));
					}
					if (!showAll && categoryPoiAdditionals.size() > 0) {
						items.add(new PoiFilterListItem(PoiFilterListItemType.BUTTON_ITEM,
								0, app.getString(R.string.shared_string_show_all).toUpperCase(), groupId, false, false, false, category, null));
					}
				}
			}
		}
		return items;
	}

	private void extractPoiAdditionals(Collection<PoiType> poiAdditionals,
									   Map<String, List<PoiType>> additionalsMap,
									   Set<String> excludedPoiAdditionalCategories,
									   boolean extractAll) {
		for (PoiType poiType : poiAdditionals) {
			String category = poiType.getPoiAdditionalCategory();
			if (category == null) {
				category = "";
			}
			if (excludedPoiAdditionalCategories != null && excludedPoiAdditionalCategories.contains(category)) {
				continue;
			}
			if (collapsedCategories.contains(category) && !extractAll) {
				if (!additionalsMap.containsKey(category)) {
					additionalsMap.put(category, new ArrayList<PoiType>());
				}
				continue;
			}
			boolean showAll = showAllCategories.contains(category) || extractAll;
			String keyName = poiType.getKeyName().replace('_', ':').toLowerCase();
			if (!poiAdditionalsTranslations.containsKey(poiType)) {
				poiAdditionalsTranslations.put(poiType, poiType.getTranslation());
			}
			if (showAll || poiType.isTopVisible() || selectedPoiAdditionals.contains(keyName.replace(' ', ':'))) {
				List<PoiType> adds = additionalsMap.get(category);
				if (adds == null) {
					adds = new ArrayList<>();
					additionalsMap.put(category, adds);
				}
				if (!adds.contains(poiType)) {
					adds.add(poiType);
				}
			}
		}
	}

	private void initListItems() {
		Map<String, PoiType> poiAdditionals = filter.getPoiAdditionals();
		Set<String> excludedPoiAdditionalCategories = getExcludedPoiAdditionalCategories();
		List<PoiType> otherAdditionalCategories = getMyApplication().getPoiTypes().getOtherMapCategory().getPoiAdditionalsCategorized();
		if (poiAdditionals != null) {
			initPoiAdditionals(poiAdditionals.values(), excludedPoiAdditionalCategories);
			initPoiAdditionals(otherAdditionalCategories, excludedPoiAdditionalCategories);
		}
	}

	private void initPoiAdditionals(Collection<PoiType> poiAdditionals,
									Set<String> excludedPoiAdditionalCategories) {
		Set<String> selectedCategories = new LinkedHashSet<>();
		Set<String> topTrueOnlyCategories = new LinkedHashSet<>();
		Set<String> topFalseOnlyCategories = new LinkedHashSet<>();
		for (PoiType poiType : poiAdditionals) {
			String category = poiType.getPoiAdditionalCategory();
			if (category != null) {
				topTrueOnlyCategories.add(category);
				topFalseOnlyCategories.add(category);
			}
		}
		for (PoiType poiType : poiAdditionals) {
			String category = poiType.getPoiAdditionalCategory();
			if (category == null) {
				category = "";
			}
			if (excludedPoiAdditionalCategories != null && excludedPoiAdditionalCategories.contains(category)) {
				topTrueOnlyCategories.remove(category);
				topFalseOnlyCategories.remove(category);
				continue;
			}
			if (!poiType.isTopVisible()) {
				topTrueOnlyCategories.remove(category);
			} else {
				topFalseOnlyCategories.remove(category);
			}
			String keyName = poiType.getKeyName().replace('_', ':').replace(' ', ':').toLowerCase();
			if (selectedPoiAdditionals.contains(keyName)) {
				selectedCategories.add(category);
			}
		}
		for (String category : topTrueOnlyCategories) {
			if (!showAllCategories.contains(category)) {
				showAllCategories.add(category);
			}
		}
		for (String category : topFalseOnlyCategories) {
			if (!collapsedCategories.contains(category) && !showAllCategories.contains(category)) {
				if (!selectedCategories.contains(category)) {
					collapsedCategories.add(category);
				}
				showAllCategories.add(category);
			}
		}
	}

	public static void showDialog(DialogFragment parentFragment, String filterByName, String filterId) {
		Bundle bundle = new Bundle();
		if (filterByName != null) {
			bundle.putString(QUICK_SEARCH_POI_FILTER_BY_NAME_KEY, filterByName);
		}
		if (filterId != null) {
			bundle.putString(QUICK_SEARCH_POI_FILTER_ID_KEY, filterId);
		}
		QuickSearchPoiFilterFragment fragment = new QuickSearchPoiFilterFragment();
		fragment.setArguments(bundle);
		fragment.show(parentFragment.getChildFragmentManager(), TAG);
	}

	private class PoiFilterListAdapter extends ArrayAdapter<PoiFilterListItem> {
		private OsmandApplication app;

		PoiFilterListAdapter(OsmandApplication app, List<PoiFilterListItem> items) {
			super(app, R.layout.poi_filter_list_item, items);
			this.app = app;
		}

		public void setListItems(List<PoiFilterListItem> items) {
			setNotifyOnChange(false);
			clear();
			for (PoiFilterListItem item : items) {
				add(item);
			}
			setNotifyOnChange(true);
			notifyDataSetChanged();
		}

		@Override
		public int getItemViewType(int position) {
			PoiFilterListItem item = getItem(position);
			return item != null && item.type == PoiFilterListItemType.DIVIDER ? 1 : 0;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		public void toggleSwitch(PoiFilterListItem item, boolean isChecked) {
			item.checked = isChecked;
			if (item.checked) {
				selectedPoiAdditionals.add(item.keyName);
			} else {
				selectedPoiAdditionals.remove(item.keyName);
			}
			updateApplyButton();
		}

		public void toggleCheckbox(PoiFilterListItem item, CheckBox checkBox, boolean isChecked) {
			if (checkBox != null) {
				checkBox.setChecked(isChecked);
			}
			item.checked = isChecked;
			if (item.checked) {
				selectedPoiAdditionals.add(item.keyName);
			} else {
				selectedPoiAdditionals.remove(item.keyName);
			}
			updateApplyButton();
		}

		@NonNull
		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			final PoiFilterListItem item = getItem(position);
			final PoiFilterListItem nextItem = position < getCount() - 1 ? getItem(position + 1) : null;

			int viewType = getItemViewType(position);

			View view;
			if (convertView == null) {
				LayoutInflater inflater = (LayoutInflater) app.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				if (viewType == 0) {
					view = inflater.inflate(R.layout.poi_filter_list_item, null);
				} else {
					view = inflater.inflate(R.layout.list_item_divider, null);
					view.setOnClickListener(null);
				}
			} else {
				view = convertView;
			}

			if (viewType == 1) {
				return view;
			}

			final ImageView icon = (ImageView) view.findViewById(R.id.icon);
			final TextViewEx titleRegular = (TextViewEx) view.findViewById(R.id.titleRegular);
			final TextViewEx titleBold = (TextViewEx) view.findViewById(R.id.titleBold);
			final TextViewEx titleButton = (TextViewEx) view.findViewById(R.id.titleButton);
			final SwitchCompat switchItem = (SwitchCompat) view.findViewById(R.id.switchItem);
			final CheckBox checkBoxItem = (CheckBox) view.findViewById(R.id.checkboxItem);
			final ImageView expandItem = (ImageView) view.findViewById(R.id.expandItem);
			final View divider = view.findViewById(R.id.divider);

			if (item != null) {
				if (nextItem != null && nextItem.groupIndex == item.groupIndex) {
					divider.setVisibility(View.VISIBLE);
				} else {
					divider.setVisibility(View.GONE);
				}

				if (item.iconId != 0) {
					icon.setImageDrawable(app.getIconsCache().getIcon(item.iconId,
							app.getSettings().isLightContent() ? R.color.icon_color : R.color.color_white));
					icon.setVisibility(View.VISIBLE);
				} else {
					icon.setVisibility(View.GONE);
				}
				switchItem.setOnCheckedChangeListener(null);
				checkBoxItem.setOnCheckedChangeListener(null);
				switch (item.type) {
					case GROUP_HEADER:
						titleBold.setText(item.text);
						if (item.expandable) {
							expandItem.setImageDrawable(item.expanded ?
									app.getIconsCache().getThemedIcon(R.drawable.ic_action_arrow_up) : app.getIconsCache().getThemedIcon(R.drawable.ic_action_arrow_down));
							expandItem.setVisibility(View.VISIBLE);
						} else {
							expandItem.setVisibility(View.GONE);
						}
						titleBold.setVisibility(View.VISIBLE);
						titleButton.setVisibility(View.GONE);
						titleRegular.setVisibility(View.GONE);
						switchItem.setVisibility(View.GONE);
						checkBoxItem.setVisibility(View.GONE);
						break;
					case SWITCH_ITEM:
						titleRegular.setText(item.text);
						titleRegular.setVisibility(View.VISIBLE);
						switchItem.setVisibility(View.VISIBLE);
						switchItem.setChecked(item.checked);
						switchItem.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								toggleSwitch(item, isChecked);
							}
						});
						titleBold.setVisibility(View.GONE);
						titleButton.setVisibility(View.GONE);
						expandItem.setVisibility(View.GONE);
						checkBoxItem.setVisibility(View.GONE);
						break;
					case CHECKBOX_ITEM:
						titleRegular.setText(item.text);
						titleRegular.setVisibility(View.VISIBLE);
						checkBoxItem.setVisibility(View.VISIBLE);
						checkBoxItem.setChecked(item.checked);
						checkBoxItem.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
								toggleCheckbox(item, checkBoxItem, isChecked);
							}
						});
						switchItem.setVisibility(View.GONE);
						titleBold.setVisibility(View.GONE);
						titleButton.setVisibility(View.GONE);
						expandItem.setVisibility(View.GONE);
						break;
					case BUTTON_ITEM:
						titleButton.setText(item.text);
						titleButton.setVisibility(View.VISIBLE);
						checkBoxItem.setVisibility(View.GONE);
						switchItem.setVisibility(View.GONE);
						titleBold.setVisibility(View.GONE);
						titleRegular.setVisibility(View.GONE);
						expandItem.setVisibility(View.GONE);
						break;
				}
			}
			return view;
		}
	}

	public enum PoiFilterListItemType {
		DIVIDER,
		GROUP_HEADER,
		SWITCH_ITEM,
		CHECKBOX_ITEM,
		BUTTON_ITEM
	}

	public static class PoiFilterListItem {
		private PoiFilterListItemType type;
		private int iconId;
		private String text;
		private int groupIndex;
		private boolean expandable;
		private boolean expanded;
		private boolean checked;
		private String category;
		private String keyName;

		public PoiFilterListItem(PoiFilterListItemType type, int iconId, String text, int groupIndex,
								 boolean expandable, boolean expanded, boolean checked, String category,
								 String keyName) {
			this.type = type;
			this.iconId = iconId;
			this.text = text;
			this.groupIndex = groupIndex;
			this.expandable = expandable;
			this.expanded = expanded;
			this.checked = checked;
			this.category = category;
			this.keyName = keyName;
		}
	}
}
