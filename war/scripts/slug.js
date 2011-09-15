"use strict";

/* Open a channel to receive an updated list of slugs in use.
 Compare current slug field value to switch between "Move" and "Overwrite"
 text on the submit button. Also change slug field background color.

 Actions on key input:
 - Reject invalid chars.
 - Enable submit button if all 3 fields are filled, else make sure it's disabled. */

// To be defined prior to referencing this file:
// channel = new goog.appengine.Channel('token');

var titleInput = $('[name="title"]');
var slugInput = $('[name="slug"]');
var textArea = $('[name="body"]');
var submitButton = $('[type="submit"]');

var currentSlug = slugInput.val();

function getSlugs(){
    $.ajax({type: 'GET',
	    url: "/admin/slugs",
	    success: function(data){
		var Slugs = data.split(" ");
		gotSlugs(Slugs);
	    },
	    error: function(){
		function(){$('body').prepend('<p>Failed to get slugs</p>');};
	    }
	   });
}

// The following is only called once a channel and Slugs exists.
function gotSlugs(Slugs){
    function convertToSlug(Text){
	return Text
            .toLowerCase()
            .replace(/ /g,'_')
            .replace(/_+/g,'_')
            .replace(/[^\w-]+/g,'');
    }

    function updateSubmitButton(Slug){
	if (slugInput.val() != ''){
	    // .disabled only works with [0], whereas .val only works without!?
	    submitButton[0].disabled = false;
	}
	else {
	    submitButton[0].disabled = true;
	}

	if (jQuery.inArray(Slug, Slugs) > -1) {
            // slug in use, warn
            slugInput.addClass('warning');
            submitButton.val("Can't overwrite existing Article");
	    submitButton[0].disabled = true;
        }
        else {
            // slug not in use
            slugInput.removeClass('warning');
            submitButton.val(SubmitDefaultValue);
        }
    }

    function rejectInvalidChars(o, regexp){
	o.value = o.value.replace(regexp, '');
    }

    var SubmitDefaultValue = submitButton.val();

    slugInput.keyup(function(){
			updateSubmitButton($(this).val());
			return rejectInvalidChars(this, /[^a-zäöüß0-9_\-]/);
		    });

    // Submit button, trigger changing the slug
    function submit() {
	$.post("/admin/move", {from: currentSlug,
			       to: slugInput.val()},
	       function(data){location.href = '/' + slugInput.val();});
    }

    submitButton[0].onclick = function() { submit(); };
}

var socket = channel.open();
socket.onopen = function(){getSlugs();};
socket.onmessage = function(){getSlugs();};
socket.onerror = function(){$('body').prepend('<p>Channel error, please reload.</p>');};
socket.onclose = function(){$('body').prepend('<p>Channel closed, please reload.</p>');};