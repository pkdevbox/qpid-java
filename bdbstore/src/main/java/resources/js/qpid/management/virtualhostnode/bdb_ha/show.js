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
define(["dojo/_base/xhr",
        "dojo/_base/lang",
        "dojo/_base/connect",
        "dojo/parser",
        "dojo/string",
        "dojox/html/entities",
        "dojo/query",
        "dojo/json",
        "dijit/registry",
        "dojox/grid/EnhancedGrid",
        "qpid/common/UpdatableStore",
        "qpid/management/UserPreferences",
        "qpid/management/virtualhostnode/bdb_ha/edit",
        "dojo/domReady!"],
  function (xhr, lang, connect, parser, json, entities, query, json, registry, EnhancedGrid, UpdatableStore, UserPreferences, edit)
  {
    var nodeFields = ["storePath", "groupName", "role", "address", "coalescingSync", "designatedPrimary", "durability", "priority", "quorumOverride"];

    function findNode(nodeClass, containerNode)
    {
      return query("." + nodeClass, containerNode)[0];
    }

    function sendRequest(nodeName, remoteNodeName, method, attributes)
    {
        var success = false;
        var failureReason = "";
        var url = null;
        if (nodeName == remoteNodeName)
        {
          url = "api/latest/virtualhostnode/" + encodeURIComponent(nodeName);
        }
        else
        {
          url = "api/latest/replicationnode/" + encodeURIComponent(nodeName) + "/" + encodeURIComponent(remoteNodeName);
        }

        if (method == "POST")
        {
          xhr.put({
            url: url,
            sync: true,
            handleAs: "json",
            headers: { "Content-Type": "application/json"},
            putData: json.stringify(attributes),
            load: function(x) {success = true; },
            error: function(error) {success = false; failureReason = error;}
          });
        }
        else if (method == "DELETE")
        {
          xhr.del({url: url, sync: true, handleAs: "json"}).then(
                function(data) { success = true; },
                function(error) {success = false; failureReason = error;}
          );
        }

        if (!success)
        {
            alert("Error:" + failureReason);
        }
    }

    function BDBHA(containerNode) {
      var that = this;
      xhr.get({url: "virtualhostnode/bdb_ha/show.html",
        sync: true,
        load:  function(template) {
          containerNode.innerHTML = template;
          parser.parse(containerNode);
        }});

      for(var i=0; i<nodeFields.length;i++)
      {
        var fieldName = nodeFields[i];
        this[fieldName]= findNode(fieldName, containerNode);
      }

      this.designatedPrimaryContainer = findNode("designatedPrimaryContainer", containerNode);
      this.priorityContainer = findNode("priorityContainer", containerNode);
      this.quorumOverrideContainer = findNode("quorumOverrideContainer", containerNode);
      this.environmentConfigurationPanel = registry.byNode(query(".environmentConfigurationPanel", containerNode)[0]),
      this.environmentConfigurationGrid = new UpdatableStore([],
          query(".environmentConfiguration", containerNode)[0],
          [ {name: 'Name', field: 'id', width: '50%'}, {name: 'Value', field: 'value', width: '50%'} ],
          null,
          null,
          null, true );
      this.replicatedEnvironmentConfigurationPanel = registry.byNode(query(".replicatedEnvironmentConfigurationPanel", containerNode)[0]);
      this.replicatedEnvironmentConfigurationGrid = new UpdatableStore([],
          query(".replicatedEnvironmentConfiguration", containerNode)[0],
          [ {name: 'Name', field: 'id', width: '50%'}, {name: 'Value', field: 'value', width: '50%'} ],
          null,
          null,
          null, true );

      this.membersGridPanel = registry.byNode(query(".membersGridPanel", containerNode)[0]);
      this.membersGrid = new UpdatableStore([],
          findNode("groupMembers", containerNode),
          [
           { name: 'Name', field: 'name', width: '10%' },
           { name: 'Role', field: 'role', width: '10%' },
           { name: 'Address', field: 'address', width: '35%' },
           { name: 'Join Time', field: 'joinTime', width: '25%', formatter: function(value){ return value ? UserPreferences.formatDateTime(value) : "";} },
           { name: 'Replication Transaction ID', field: 'lastKnownReplicationTransactionId', width: '20%' }
          ],
          null,
          {
            selectionMode: "single",
            keepSelection: true,
            plugins: {
              indirectSelection: true
            }
          },
          EnhancedGrid, true );

      this.removeNodeButton = registry.byNode(query(".removeNodeButton", containerNode)[0]);
      this.transferMasterButton = registry.byNode(query(".transferMasterButton", containerNode)[0]);
      this.transferMasterButton.set("disabled", true);
      this.removeNodeButton.set("disabled", true);

      var nodeControlsToggler = function(rowIndex)
      {
        var data = that.membersGrid.grid.selection.getSelected();
        that.transferMasterButton.set("disabled", data.length != 1|| data[0].role != "REPLICA");
        that.removeNodeButton.set("disabled", data.length != 1 || data[0].role == "MASTER" || data[0].name ==  that.data.name);
      };
      connect.connect(this.membersGrid.grid.selection, 'onSelected',  nodeControlsToggler);
      connect.connect(this.membersGrid.grid.selection, 'onDeselected',  nodeControlsToggler);

      this.transferMasterButton.on("click",
          function(e)
          {
            var data = that.membersGrid.grid.selection.getSelected();
            if (data.length == 1 && confirm("Are you sure you would like to transfer mastership to node '" + data[0].name + "'?"))
            {
                sendRequest(that.data.name, data[0].name, "POST", {role: "MASTER"});
                that.membersGrid.grid.selection.clear();
            }
          }
      );

      this.removeNodeButton.on("click",
          function(e){
            var data = that.membersGrid.grid.selection.getSelected();
            if (data.length == 1 && confirm("Are you sure you would like to delete node '" + data[0].name + "'?"))
            {
                sendRequest(that.data.name, data[0].name, "DELETE");
                that.membersGrid.grid.selection.clear();
            }
          }
      );

      this.stopNodeButton = registry.byNode(findNode("stopNodeButton", containerNode));
      this.startNodeButton = registry.byNode(findNode("startNodeButton", containerNode));
      this.editNodeButton = registry.byNode(findNode("editNodeButton", containerNode));
      this.editNodeButton.on("click",
          function(e)
          {
            edit.show(that.data.name);
          }
      );
      this.deleteNodeButton = registry.byNode(query(".deleteNodeButton", containerNode)[0]);
      this.deleteNodeButton.on("click",
          function(e)
          {
            if (confirm("Deletion of virtual host node will delete both configuration and message data.\n\n Are you sure you want to delete virtual host node?"))
            {
              sendRequest(that.data.name, that.data.name, "DELETE");
              // TODO: close tab
            }
          }
      );

    }

    BDBHA.prototype.update=function(data)
    {
      this.data = data;
      for(var i = 0; i < nodeFields.length; i++)
      {
        var name = nodeFields[i];
        this[name].innerHTML = entities.encode(String(data[name]));
      }

      this._updateGrid(this._convertConfig(data.environmentConfiguration), this.environmentConfigurationPanel, this.environmentConfigurationGrid );
      this._updateGrid(this._convertConfig(data.replicatedEnvironmentConfiguration), this.replicatedEnvironmentConfigurationPanel, this.replicatedEnvironmentConfigurationGrid );

      var members = data.remotereplicationnodes;
      if (members)
      {
        members.push({
          id: data.id,
          name: data.name,
          groupName: data.groupName,
          address: data.address,
          role: data.role,
          joinTime: data.joinTime,
          lastKnownReplicationTransactionId: data.lastKnownReplicationTransactionId
        });
      }
      this._updateGrid(members, this.membersGridPanel, this.membersGrid);

      if (!members || members.length < 3)
      {
        this.designatedPrimaryContainer.style.display="block";
        this.priorityContainer.style.display="none";
        this.quorumOverrideContainer.style.display="none";
      }
      else
      {
        this.designatedPrimaryContainer.style.display="none";
        this.priorityContainer.style.display="block";
        this.quorumOverrideContainer.style.display="block";
      }
      this.deleteNodeButton.set("disabled", data.role=="MASTER");
    };

    BDBHA.prototype._updateGrid=function(conf, panel, updatableGrid)
    {
      if (conf && conf.length > 0)
      {
        panel.domNode.style.display="block";
        var changed = updatableGrid.update(conf);
        if (changed)
        {
          updatableGrid.grid._refresh();
        }
      }
      else
      {
        panel.domNode.style.display="none";
      }
    }

    BDBHA.prototype._convertConfig=function(conf)
    {
      var settings = [];
      if (conf)
      {
        for(var propName in conf)
        {
          if(conf.hasOwnProperty(propName))
          {
            settings.push({"id": propName, "value": conf[propName]});
          }
        }
      }
      return settings;
    }
    return BDBHA;
});
