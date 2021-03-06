(ns deathstar.ui.tournament.render
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [goog.string.format :as format]
   [goog.string :refer [format]]
   [goog.object]
   [cljs.reader :refer [read-string]]
   [clojure.pprint :refer [pprint]]
   [reagent.core :as r]
   [reagent.dom :as rdom]

   [cljctools.csp.op.spec :as op.spec]
   [cljctools.cljc.core :as cljc.core]


   [deathstar.app.spec :as app.spec]
   [deathstar.app.chan :as app.chan]

   [deathstar.ui.spec :as ui.spec]

   [deathstar.scenario-api.spec :as scenario-api.spec]
   [deathstar.scenario-api.chan :as scenario-api.chan]

   ["react" :as React]
   ["react-router-dom" :as ReactRouter :refer [BrowserRouter
                                               HashRouter
                                               Switch
                                               Route
                                               Link
                                               useLocation
                                               useHistory
                                               useRouteMatch
                                               useParams]]
   ["antd/lib/layout" :default AntLayout]
   ["antd/lib/menu" :default AntMenu]
   ["antd/lib/icon" :default AntIcon]
   ["antd/lib/button" :default AntButton]
   ["antd/lib/list" :default AntList]
   ["antd/lib/row" :default AntRow]
   ["antd/lib/col" :default AntCol]
   ["antd/lib/form" :default AntForm]
   ["antd/lib/input" :default AntInput]
   ["antd/lib/tabs" :default AntTabs]
   ["antd/lib/table" :default AntTable]

   ["antd/lib/checkbox" :default AntCheckbox]


   ["antd/lib/divider" :default AntDivider]
   ["@ant-design/icons/SmileOutlined" :default AntIconSmileOutlined]
   ["@ant-design/icons/LoadingOutlined" :default AntIconLoadingOutlined]
   ["@ant-design/icons/SyncOutlined" :default AntIconSyncOutlined]
   ["@ant-design/icons/ReloadOutlined" :default AntIconReloadOutlined]))

(do
  (def ant-row (reagent.core/adapt-react-class AntRow))
  (def ant-col (reagent.core/adapt-react-class AntCol))
  (def ant-divider (reagent.core/adapt-react-class AntDivider))
  (def ant-layout (reagent.core/adapt-react-class AntLayout))
  (def ant-layout-content (reagent.core/adapt-react-class (.-Content AntLayout)))
  (def ant-layout-header (reagent.core/adapt-react-class (.-Header AntLayout)))

  (def ant-menu (reagent.core/adapt-react-class AntMenu))
  (def ant-menu-item (reagent.core/adapt-react-class (.-Item AntMenu)))
  (def ant-icon (reagent.core/adapt-react-class AntIcon))
  (def ant-button (reagent.core/adapt-react-class AntButton))
  (def ant-button-group (reagent.core/adapt-react-class (.-Group AntButton)))
  (def ant-list (reagent.core/adapt-react-class AntList))
  (def ant-input (reagent.core/adapt-react-class AntInput))
  (def ant-input-password (reagent.core/adapt-react-class (.-Password AntInput)))
  (def ant-checkbox (reagent.core/adapt-react-class AntCheckbox))
  (def ant-form (reagent.core/adapt-react-class AntForm))
  (def ant-table (reagent.core/adapt-react-class AntTable))
  (def ant-form-item (reagent.core/adapt-react-class (.-Item AntForm)))
  (def ant-tabs (reagent.core/adapt-react-class AntTabs))
  (def ant-tab-pane (reagent.core/adapt-react-class (.-TabPane AntTabs)))

  (def ant-icon-smile-outlined (reagent.core/adapt-react-class AntIconSmileOutlined))
  (def ant-icon-loading-outlined (reagent.core/adapt-react-class AntIconLoadingOutlined))
  (def ant-icon-sync-outlined (reagent.core/adapt-react-class AntIconSyncOutlined))
  (def ant-icon-reload-outlined (reagent.core/adapt-react-class AntIconReloadOutlined)))

(defn table-peer-metas-columns
  [channels state*]
  [{:title "peer-id"
    :key ::app.spec/peer-id
    :dataIndex ::app.spec/peer-id}
   {:title "counter"
    :key ::app.spec/counter
    :dataIndex ::app.spec/counter}])

(defn table-peer-metas
  [channels state*]
  (reagent.core/with-let
    [{:keys [:path :url :isExact :params]} (js->clj (useRouteMatch)
                                                    :keywordize-keys true)
     frequency (:frequency params)
     peer-metas* (reagent.core/cursor state* [::app.spec/tournaments frequency ::app.spec/peer-metas])
     columns (vec (concat (table-peer-metas-columns channels state*)))]
    (let [dataSource (vec (vals @peer-metas*))
          total (count dataSource)]
      [ant-table {:show-header true
                  :size "small"
                  :row-key ::app.spec/peer-id
                  :style {:height "50%" :width "100%"}
                  :columns columns
                  :dataSource dataSource
                  :on-change (fn [pag fil sor ext]
                               #_(js->clj {:pagination pag
                                           :filters fil
                                           :sorter sor
                                           :extra ext} :keywordize-keys true))
                  :scroll {;  :x "max-content" 
                                ;  :y 256
                           }
                  :pagination false}])))


(defn rc-page
  [channels state*]
  (reagent.core/with-let
    [scenario-origin (reagent.core/cursor state* [::ui.spec/scenario-origin])]
    [:<>
     [ant-button {:type "default"
                  :size "small"
                  :on-click (fn []
                              (app.chan/op
                               {::op.spec/op-key ::app.chan/create-game
                                ::op.spec/op-type ::op.spec/fire-and-forget}
                               channels
                               {}))} "create game"]
     [ant-row {:justify "center"
               :align "top" #_"middle"
               :style {:height "40%"}
                    ;; :gutter [16 24]
               }
      [ant-col {:span 24}
       [:f> table-peer-metas channels state*]]]]))