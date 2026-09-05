{{/*
 OMOBIO Helm helpers
*/}}

{{- define "omobio.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "omobio.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "omobio.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end }}

{{- define "omobio.labels" -}}
helm.sh/chart: {{ include "omobio.chart" . }}
app.kubernetes.io/name: {{ include "omobio.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.omobio.io/component: microservice
{{- end }}

{{- define "omobio.selectorLabels" -}}
app.kubernetes.io/name: {{ include "omobio.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "omobio.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "omobio.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "omobio.podLabels" -}}
{{ include "omobio.selectorLabels" . }}
{{- if .Values.podLabels }}
{{- toYaml .Values.podLabels }}
{{- end }}
{{- end }}

{{- define "omobio.namespace" -}}
{{- .Release.Namespace }}
{{- end }}

{{- define "omobio.serviceName" -}}
{{- include "omobio.fullname" . }}
{{- end }}

{{/*
Compatibility: return tenant.id or empty string
*/}}
{{- define "omobio.tenantId" -}}
{{- .Values.tenant.id | default "" }}
{{- end }}

{{/*
Compatibility: build image tag string
*/}}
{{- define "omobio.imageTag" -}}
{{- .Values.image.tag | default .Chart.AppVersion | default "latest" }}
{{- end }}
