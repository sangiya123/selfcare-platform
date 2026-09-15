{{/*
Selfcare Platform — shared Helm helpers.
Expand the name/path of a chart-linked object.
*/}}
{{- define "selfcare.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- /*
Returns the release / chart combined name for top-level objects,
unless deploy.isolated=true (Jenkins one-by-one), in which case the
object belongs to a single service and is named after that service.
*/}}
{{- define "selfcare.fullname" -}}
{{- if .Values.deploy.isolated -}}
{{- printf "%s" (.Values.deploy.service | default .Values.services.default.name) }}
{{- else -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end -}}
{{- end -}}
{{- end -}}
{{- end }}

{{- /*
Chart-wide labels (resource-level labels.
*/}}
{{- define "selfcare.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: selfcare-platform
{{- end }}

{{- /*
Labels applied to every workload of one service (the `svc` map entry).
*/}}
{{- define "selfcare.serviceLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .name }}
{{- if .tenant }}
selfcare.tenant: {{ .tenant | quote }}
{{- end }}
{{- if .industry }}
selfcare.industry: {{ .industry | quote }}
{{- end }}
{{- if .tier }}
selfcare.tier: {{ .tier | quote }}
{{- end }}
{{- end }}

{{- /*
Selector labels for one service.
*/}}
{{- define "selfcare.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
{{- end }}

{{- /*
Does this chart render THIS svc key? When deploy.isolated=false all render.
When isolated=true only the deploy.service entry renders.
*/}}
{{- define "selfcare.renderService" -}}
{{- if .Values.deploy.isolated -}}
{{- if ne .name (.Values.deploy.service | default "") -}}
false
{{- else -}}
true
{{- end -}}
{{- else -}}
true
{{- end -}}
{{- end }}

{{- /*
The image reference for a service. deploy.image.override wins (Jenkins),
or per-service image, or the global image.
*/}}
{{- define "selfcare.image" -}}
{{- $img := .Values.image -}}
{{- if .value.image -}}{{- $img = .value.image -}}{{- end -}}
{{- $repo := $img.repository -}}
{{- if .Values.deploy.image.repository -}}{{- $repo = .Values.deploy.image.repository -}}{{- end -}}
{{- $tag := $img.tag -}}
{{- if .Values.deploy.image.tag -}}{{- $tag = .Values.deploy.image.tag -}}{{- end -}}
{{- if hasSuffix (printf "/%s" .name) $repo -}}
{{- printf "%s:%s" $repo $tag -}}
{{- else -}}
{{- printf "%s/%s:%s" $repo .name $tag -}}
{{- end -}}
{{- end }}

{{- /*
Common env that every selfcare service inherits unless overridden by the
service, tenant or environment. Move credentials to a Secret referenced
through secretRef (never inline here).
*/}}
{{- define "selfcare.sharedEnv" -}}
{{- $env := .Values.environment }}
{{- if $env.name }}
- name: SPRING_PROFILES_ACTIVE
  value: {{ $env.name | quote }}
{{- end }}
{{- if $env.profiles }}
- name: SPRING_PROFILES_ACTIVE
  value: {{ $env.profiles | quote }}
{{- end }}
- name: SELFCARE_TENANT_DEFAULT_ID
  value: {{ .Values.tenant.id | default "dialog-lk" | quote }}
- name: X_DEFAULT_TENANT_ID
  value: {{ .Values.tenant.id | default "dialog-lk" | quote }}
{{- if .Values.tenant.industry }}
- name: SELFCARE_TENANT_INDUSTRY
  value: {{ .Values.tenant.industry | quote }}
{{- end }}
{{- if .Values.tenancy }}
- name: SELFCARE_TENANCY_MODE
  value: {{ .Values.tenancy | quote }}
{{- end }}
{{- if .Values.observability.logLevel }}
- name: LOG_LEVEL
  value: {{ .Values.observability.logLevel | quote }}
{{- end }}
{{- if .Values.jvm }}
- name: JAVA_TOOL_OPTIONS
  value: {{ .Values.jvm.options | default "-Xmx768m -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" | quote }}
{{- end }}
{{- if .Values.allowlist }}
- name: SELFCARE_SECURITY_URL_ALLOWLIST_HOSTS
  value: {{ .Values.allowlist | quote }}
{{- end }}
{{- end }}