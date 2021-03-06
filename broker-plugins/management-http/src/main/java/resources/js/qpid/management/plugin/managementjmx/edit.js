/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
define(["dojo/_base/event",
        "dojo/dom",
        "dojo/dom-construct",
        'dojo/json',
        "dojo/query",
        "dojo/parser",
        "dijit/registry",
        "qpid/common/util",
        "dojo/text!plugin/managementjmx/edit.html",
        "dojox/html/entities",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dijit/form/NumberSpinner",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (event, dom, domConstruct, json, query, parser,  registry, util, template, entities)
  {

    var jmxManagementEditor =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode).then(function(instances){ that._postParse();});
      },
      _postParse: function()
      {
        var that=this;
        this.allFieldsContainer = dom.byId("editJmxManagement.contentPane");
        this.dialog = registry.byId("editJmxManagementDialog");
        this.saveButton = registry.byId("editJmxManagement.saveButton");
        this.cancelButton = registry.byId("editJmxManagement.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.saveButton.on("click", function(e){that._save(e);});
        this.form = registry.byId("editJmxManagementForm");
        this.form.on("submit", function(){return false;});
      },
      show: function(management, modelObj, data)
      {
        this.management = management;
        this.modelObj = modelObj;
        var that=this;
        management.load(modelObj,  { actuals: true }).then(
            function(actualData)
            {
                that._show(actualData[0]);
            });
      },
      destroy: function()
      {
        if (this.dialog)
        {
            this.dialog.destroyRecursive();
            this.dialog = null;
        }

        if (this.containerNode)
        {
            domConstruct.destroy(this.containerNode);
            this.containerNode = null;
        }
      },
      _cancel: function(e)
      {
          this.dialog.hide();
      },
      _save: function(e)
      {
          event.stop(e);
          if(this.form.validate())
          {
              var data = util.getFormWidgetValues(this.form, this.initialData);
              var that=this;
              this.management.update(this.modelObj, data).then(function(x){ that.dialog.hide();} );
          }
          else
          {
              alert('Form contains invalid data.  Please correct first');
          }
      },
      _show:function(actualData)
      {
          this.initialData = actualData;
          util.applyToWidgets(this.allFieldsContainer,  "Plugin", "MANAGEMENT-JMX", actualData, this.management.metadata);
          this.dialog.startup();
          this.dialog.show();
          if (!this.resizeEventRegistered)
          {
            this.resizeEventRegistered = true;
            util.resizeContentAreaAndRepositionDialog(dom.byId("editJmxManagement.contentPane"), this.dialog);
          }
      }
    };

    jmxManagementEditor.init();

    return jmxManagementEditor;
  }
);
