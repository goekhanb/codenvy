define(["underscore","views/form","models/contact"],function(_,Form,Contact){

    var ContactForm = Form.extend({

        settings : {
            invalidEmailErrorMessage : "Please provide a valid email address so we can get back to you",
            invalidMessageErrorMessage : "Please write something",
            failedToSendErrorMessage : "Something went wrong. Your message was not sent. Please try again later."
        },

        __validationRules : function(){
            return {
                email: {
                    required: true,
                    email: true
                },
                message: {
                    required : true
                }
            };
        },

        __validationMessages : function(){
            return {
                email : {
                    required : this.settings.invalidEmailErrorMessage,
                    email : this.settings.invalidEmailErrorMessage
                },
                message : {
                    required : this.settings.invalidMessageErrorMessage
                }
            };
        },

        __submit : function(form){

            this.__showProgress();

            this.trigger("submitting");

            Contact.sendMessage(
                this.$("input[name='email']").val(),
                this.$("textarea[name='message']").val(),
                _.bind(function(){
                    this.$(".success-message").removeClass("hidden");
                    this.__restoreForm();
                    var cleanContactForm = document.forms[0];
                    cleanContactForm.elements["email"].value = "";
                    cleanContactForm.elements["message"].value = "";
                },this),
                _.bind(function(){
                    this.trigger("invalid",null,this.settings.failedToSendErrorMessage);
                    this.__restoreForm();
                },this)
            );
        },

        __showErrors : function(errorMap, errorList){
            if(typeof errorMap.email !== 'undefined'){
                this.trigger("invalid","email",errorMap.email);
                return;
            }

            if(typeof errorMap.message !== 'undefined'){
                this.trigger("invalid","message",errorMap.message);
                return;
            }
        },

        __restoreForm : function(){
            this.$("input[type='submit']").removeAttr("disabled");
        },

        __showProgress : function(){
            this.$("input[type='submit']").attr("disabled","disabled");
        }
    });

    return {
        get : function(el){
            if(typeof el === 'undefined'){
                throw new Error("Need an element");
            }

            return new ContactForm({ el : el });
        },

        ContactForm : ContactForm
    };

});
