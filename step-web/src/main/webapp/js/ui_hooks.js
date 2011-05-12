/**
 * This file defines a number of hooks that the server code can call.
 * The aim is to redirect the calls quickly to other parts of the UI
 * 
 * The other calls here are for the menu system which are executing in any particular context
 */

/////////////////////////////////////////////////////////////////////////
// The following section defines method names and controller names
// These are used as part of the rest-like calls
/////////////////////////////////////////////////////////////////////////
BOOKMARKS_GET = "rest/favourites/getBookmarks";
BOOKMARKS_ADD = "rest/favourites/addBookmark/";
HISTORY_GET = "rest/favourites/getHistory/";
HISTORY_ADD = "rest/favourites/addHistory/";


BIBLE_GET_BIBLE_VERSIONS = "rest/bible/getBibleVersions/";
BIBLE_GET_BIBLE_TEXT = "rest/bible/getBibleText/";
BIBLE_GET_FEATURES = "rest/bible/getFeatures/";
BIBLE_GET_ALL_FEATURES = "rest/bible/getAllFeatures/";

MODULE_GET_ALL_MODULES = "rest/module/getAllModules/";
MODULE_GET_ALL_INSTALLABLE_MODULES = "rest/module/getAllModules/";
MODULE_GET_DEFINITION = "rest/module/getDefinition/";

SETUP_IS_FIRST_TIME = "rest/setup/isFirstTime/";
SETUP_INSTALL_DEFAULT_MODULES = "rest/setup/installDefaultModules/";
SETUP_INSTALL_BIBLE = "rest/setup/installBible/";

TIMELINE_GET_EVENTS = "rest/timeline/getEvents/";
TIMELINE_GET_EVENTS_FROM_REFERENCE = "rest/timeline/getEventsFromReference/";
TIMELINE_GET_CONFIG = "rest/timeline/getTimelineConfiguration";

USER_LOGIN = "rest/user/login/";
USER_LOGOUT = "rest/user/logout/";
USER_REGISTER = "rest/user/register/"
USER_GET_LOGGED_IN_USER = "rest/user/getLoggedInUser";

GEOGRAPHY_GET_PLACES = "rest/geography/getPlaces/"
	
//////////////////////////
// SOME DEFAULTS
//////////////////////////
var DEFAULT_POPUP_WIDTH = 500;

/**
 * a helper function that returns the passageId relevant to the menu item provided
 * @param menuItem the menuItem 
 * @return id of the passage
 */
function getPassageId(menuItem) {
	return $(menuItem).closest(".passageContainer").attr("passage-id");
}


/** a simpler toggler for the menu items */
function toggleMenuItem(menuItem) {
	//the hook needs to find the passage id
	$.shout("pane-menu-toggle-item-" + getPassageId(menuItem), menuItem.name);
};

/**
 * shows the login popup
 */
function login() {
	$.shout("show-login-popup");
};

/**
 * shows the interlinear options as a popup
 * @param menuItem the menuItem to tick if the popup selects options
 */
function showInterlinearChoices(menuItem) {
	//get passage id from menu parent
	$.shout("interlinear-menu-option-triggered-" + getPassageId(menuItem)); 
};

/**
 * called when click on a piece of text.
 * @param strongMorphs all the strongs and morphs associated with this "word"
 */
function showAllStrongMorphs(strongMorphs) {
	$.shout("show-all-strong-morphs", strongMorphs);
};

/**
 * Called when clicking on a strong link
 * @param strong strong to be displayed
 */
function showStrong(strong, sourceElement) {
	showMorphOrStrong(strong, sourceElement);
};

/**
 * called when clicking on a morph
 * @param morph the moprh that is clicked on
 */
function showMorph(morph, sourceElement) {
	showMorphOrStrong(morph, sourceElement);
};

/** TODO: move this out of here to utils.js if we have more utility classes/functions 
 * helper function for morph and strongs 
*/
function showMorphOrStrong(tag, sourceElement) {
	//trigger the parent event - to show everything
	$(sourceElement).parent().click();
	
	//need to find what event is coming in, to get the clicked element and pass that down
	$("#lexiconDefinition span:contains(" + tag + ")").parent().click();	
};

function showAbout() {
	//show popup for About box
	$( "#about" ).dialog({ 
		buttons: { "Ok": function() { $(this).dialog("close"); } },
		width: DEFAULT_POPUP_WIDTH,
		title: "STEP :: Scripture Tools for Every Pastor",
	});
};

/**
 * Shows the timeline module
 */
function showTimelineModule() {
	showBottomSection();
	$.shout("show-timeline");
};

/**
 * shows the geography module
 */
function showGeographyModule(menuItem) {
	showBottomSection();
	$.shout("show-maps", { passageId : + getPassageId(menuItem) } );
};

/**
 * shows the bottom section
 */
function showBottomSection() {
	var bottomSection = $("#bottomSection");
	bottomSection.height(bottomSection.parent().parent().height() / 2);
	refreshLayout();
}
