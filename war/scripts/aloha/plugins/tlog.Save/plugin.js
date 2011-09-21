/**
 * register the plugin with unique name
 */
GENTICS.Aloha.Save = new GENTICS.Aloha.Plugin('tlog.Save');

/**
 * Configure the available languages
 */
GENTICS.Aloha.Save.languages = ['en'];

/**
 * Initialize the plugin and set initialize flag on true
 */
GENTICS.Aloha.Save.init = function () {
    // Create a new button
    var that = this;

    var button = new GENTICS.Aloha.ui.Button({'label' : 'Save',
					      'size' : 'small',
					      'onclick' : function () {
						  post();
					      }
					     });

    // Add it to the floating menu
    GENTICS.Aloha.FloatingMenu.addButton(
      'GENTICS.Aloha.continuoustext',
      button,
      GENTICS.Aloha.i18n(GENTICS.Aloha, 'floatingmenu.tab.format'),
      4
    );
};

function post() {
    var activeID = GENTICS.Aloha.getActiveEditable().getId();
    var activeEditable = GENTICS.Aloha.getEditableById(activeID);
    var activeContent = activeEditable.getContents();
    var activeModified = activeEditable.isModified();

    if (activeID.match(/comment-body_/)) {
	postComment(activeID, activeEditable, activeContent, activeModified);
    } else {
	postArticle(activeID, activeEditable, activeContent, activeModified);
    }
}

function postComment(activeID, activeEditable, activeContent, activeModified) {
    // Post comment, if it has been modified:
    if (activeModified) {
	var commentData = {id: activeID.replace(/comment-body_/, ""),
			   body: activeContent.replace(/<br>$/, '') // Drop traling <br>
    			   //author: document.getElementById(authorId).value,
    			   //link: document.getElementById(linkId).value,
			   };

	$.post('/admin/update-comment', commentData);
    } else {
	alert('No changes to save!');
    }
}

function postArticle(activeID, activeEditable, activeContent, activeModified) {
    if (activeID.match(/title_/)) {
	// The active editable contains the title, so get the body, too:
	var otherID = activeID.replace(/title_/, "");
	var postID = otherID;
	var otherEditable = GENTICS.Aloha.getEditableById(otherID);
	var otherContent = otherEditable.getContents();
	var otherModified = otherEditable.isModified();
	var content = [activeContent, otherContent];
    } else {
	// The active editable contains the body, so get the title, too:
    	var otherID = "title_" + activeID;
	var postID = activeID;
	// Title might not be in an editable, so fall back to JQuery, if getEditablebyID returns null:
	var tryOtherEditable = GENTICS.Aloha.getEditableById(otherID);
	if (tryOtherEditable) {
	    var otherContent = tryOtherEditable.getContents();
	    var otherModified = tryOtherEditable.isModified();
	} else {
	    var otherContent = $('#' + otherID).html();
	    var otherModified = false;
	}
	var content = [otherContent, activeContent];
    }

    // Post article, if at least one of title or body have been modified:
    if (activeModified || otherModified) {
	$.post('/admin/save-article', {id: postID,
				       title: content[0],
				       body: content[1]});
    } else {
	alert('No changes to save!');
    }
}